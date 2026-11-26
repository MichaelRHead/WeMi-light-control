#!/usr/bin/env python3
"""Inspect Google Play tracks and promote releases between tracks via androidpublisher v3.

Supports read-only track inspection (`list-tracks`), manual track-to-track promotion
(`promote`), and guarded event-driven promotion (`auto-promote`, Stage 7 `app_promoter`
workflow for `internal` -> `alpha` Closed testing after `APPROVED_FOR_PLAY_STORE`)
without needing the Android upload signing keystore.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any, Callable
from urllib.parse import quote

DEFAULT_PACKAGE_NAME = "com.aistudio.wemiswitch.kxpqzr"
DEFAULT_RELEASE_NOTES_FILE = "play_store_assets/RELEASE_NOTES.txt"
DEFAULT_RELEASE_NOTES_LANGUAGE = "en-US"
DEFAULT_SIGN_OFF_FILE = "RELEASE_SIGN_OFF.md"
DEFAULT_POLL_ATTEMPTS = 5
DEFAULT_POLL_INTERVAL_SEC = 20.0
PLAY_API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
PLAY_UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
PLAY_OAUTH_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
MAX_RELEASE_NOTES_CHARS = 500
VALID_RELEASE_STATUSES = ("completed", "draft", "inProgress")
VALID_NOTES_SOURCES = ("source_track", "file", "none")
REQUIRED_APPROVAL_STATUS = "APPROVED_FOR_PLAY_STORE"
TERMINAL_LIFECYCLE_STATES = (
    "RELEASE_LIFECYCLE_STATE_PUBLISHED",
    "RELEASE_LIFECYCLE_STATE_APPROVED_NOT_PUBLISHED",
    "RELEASE_LIFECYCLE_STATE_NOT_APPROVED",
)


class PlayApiError(RuntimeError):
    """Raised when the Google Play Developer API returns a non-2xx response."""

    def __init__(self, status_code: int, message: str, raw_body: str = "") -> None:
        super().__init__(message)
        self.status_code = status_code
        self.raw_body = raw_body


def validate_service_account_file(credentials_path: Path) -> str:
    """Validate that credentials_path contains a Google service account JSON key.

    Returns the service account client_email without logging any private key material.
    """
    if not credentials_path.is_file():
        raise ValueError(f"Service account credentials file not found: {credentials_path}")

    raw_text = credentials_path.read_text(encoding="utf-8").strip()
    if not raw_text:
        raise ValueError(
            "PLAY_SERVICE_ACCOUNT_JSON is empty. Upload a Google Cloud service account JSON key."
        )

    try:
        payload = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise ValueError(
            "PLAY_SERVICE_ACCOUNT_JSON is not valid JSON. Make sure you stored the full "
            "service account JSON key (not a GCP API key)."
        ) from exc

    if not isinstance(payload, dict) or payload.get("type") != "service_account":
        raise ValueError(
            "PLAY_SERVICE_ACCOUNT_JSON must have \"type\": \"service_account\" "
            "(Google Play Developer API requires OAuth 2.0 service account credentials)."
        )

    client_email = str(payload.get("client_email") or "").strip()
    private_key = str(payload.get("private_key") or "")
    if not client_email or "BEGIN PRIVATE KEY" not in private_key:
        raise ValueError(
            "PLAY_SERVICE_ACCOUNT_JSON is missing client_email or private_key fields."
        )

    return client_email


def build_authorized_session(credentials_path: Path) -> Any:
    """Create a google-auth AuthorizedSession scoped to androidpublisher."""
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    credentials = service_account.Credentials.from_service_account_file(
        str(credentials_path),
        scopes=[PLAY_OAUTH_SCOPE],
    )
    return AuthorizedSession(credentials)


def _format_api_error(status_code: int, body_text: str) -> str:
    detail = body_text.strip()
    try:
        parsed = json.loads(body_text)
        err = parsed.get("error") if isinstance(parsed, dict) else None
        if isinstance(err, dict) and err.get("message"):
            detail = str(err["message"])
    except json.JSONDecodeError:
        pass

    hints: list[str] = []
    lower = detail.lower()
    if status_code == 403 and (
        "has not been used" in lower or "is disabled" in lower or "not enabled" in lower
    ):
        hints.append(
            "Enable the Google Play Android Developer API in your GCP project: "
            "https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com"
        )
    elif status_code == 403 and "permission" in lower:
        hints.append(
            "In Play Console -> Users and permissions, confirm this service account is invited "
            "with app permissions ('View app information', 'Release apps to testing tracks', "
            "and 'Manage store presence' for Store Listing / graphic asset sync). "
            "New permissions can take up to 24 hours to propagate."
        )
    elif "changesnotsentforreview" in lower:
        hints.append(
            "Re-run with changes_not_sent_for_review=true (or resolve pending review state "
            "in Play Console -> Publishing overview)."
        )

    msg = f"Google Play API error (HTTP {status_code}): {detail}"
    if hints:
        msg += "\nHint: " + " ".join(hints)
    return msg


class PlayPublisherClient:
    """Minimal Google Play Android Publisher API v3 client for tracks, listings, and images."""

    def __init__(self, package_name: str, session: Any, timeout_sec: int = 30) -> None:
        self.package_name = package_name
        self.session = session
        self.timeout_sec = timeout_sec
        encoded_pkg = quote(package_name, safe="")
        self.base_url = f"{PLAY_API_BASE}/{encoded_pkg}"
        self.upload_base_url = f"{PLAY_UPLOAD_BASE}/{encoded_pkg}"

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, str] | None = None,
        json_body: dict[str, Any] | None = None,
        data: bytes | None = None,
        headers: dict[str, str] | None = None,
        upload: bool = False,
        timeout_sec: int | None = None,
    ) -> dict[str, Any]:
        root = self.upload_base_url if upload else self.base_url
        url = f"{root}{path}"
        req_kwargs: dict[str, Any] = {
            "params": params,
            "json": json_body,
            "timeout": timeout_sec if timeout_sec is not None else self.timeout_sec,
        }
        if data is not None:
            req_kwargs["data"] = data
        if headers is not None:
            req_kwargs["headers"] = headers
        response = self.session.request(method, url, **req_kwargs)
        text = getattr(response, "text", "") or ""
        if not (200 <= response.status_code < 300):
            raise PlayApiError(
                response.status_code,
                _format_api_error(response.status_code, text),
                raw_body=text,
            )
        if not text.strip():
            return {}
        return response.json()

    def insert_edit(self) -> str:
        data = self._request("POST", "/edits", json_body={})
        edit_id = str(data.get("id") or "").strip()
        if not edit_id:
            raise PlayApiError(500, "Google Play API did not return an edit id.")
        return edit_id

    def delete_edit(self, edit_id: str) -> None:
        try:
            self._request("DELETE", f"/edits/{quote(edit_id, safe='')}")
        except PlayApiError:
            pass

    def list_tracks(self, edit_id: str) -> list[dict[str, Any]]:
        data = self._request("GET", f"/edits/{quote(edit_id, safe='')}/tracks")
        tracks = data.get("tracks") or []
        return sorted(tracks, key=lambda item: str(item.get("track", "")))

    def list_track_releases(self, track: str) -> list[dict[str, Any]]:
        """List non-obsolete release lifecycle summaries for a track without opening an edit."""
        data = self._request("GET", f"/tracks/{quote(track, safe='')}/releases")
        return list(data.get("releases") or [])

    def get_track(self, edit_id: str, track: str) -> dict[str, Any]:
        return self._request(
            "GET",
            f"/edits/{quote(edit_id, safe='')}/tracks/{quote(track, safe='')}",
        )

    def update_track(
        self,
        edit_id: str,
        track: str,
        releases: list[dict[str, Any]],
    ) -> dict[str, Any]:
        return self._request(
            "PUT",
            f"/edits/{quote(edit_id, safe='')}/tracks/{quote(track, safe='')}",
            json_body={"track": track, "releases": releases},
        )

    def list_listings(self, edit_id: str) -> list[dict[str, Any]]:
        data = self._request("GET", f"/edits/{quote(edit_id, safe='')}/listings")
        return list(data.get("listings") or [])

    def get_listing(self, edit_id: str, language: str) -> dict[str, Any]:
        return self._request(
            "GET",
            f"/edits/{quote(edit_id, safe='')}/listings/{quote(language, safe='')}",
        )

    def update_listing(
        self,
        edit_id: str,
        language: str,
        listing: dict[str, Any],
    ) -> dict[str, Any]:
        return self._request(
            "PUT",
            f"/edits/{quote(edit_id, safe='')}/listings/{quote(language, safe='')}",
            json_body=listing,
        )

    def list_images(
        self,
        edit_id: str,
        language: str,
        image_type: str,
    ) -> list[dict[str, Any]]:
        data = self._request(
            "GET",
            f"/edits/{quote(edit_id, safe='')}/listings/{quote(language, safe='')}/{quote(image_type, safe='')}",
        )
        return list(data.get("images") or [])

    def delete_all_images(
        self,
        edit_id: str,
        language: str,
        image_type: str,
    ) -> list[dict[str, Any]]:
        data = self._request(
            "DELETE",
            f"/edits/{quote(edit_id, safe='')}/listings/{quote(language, safe='')}/{quote(image_type, safe='')}",
        )
        return list(data.get("deleted") or [])

    def upload_image(
        self,
        edit_id: str,
        language: str,
        image_type: str,
        image_path: Path,
    ) -> dict[str, Any]:
        suffix = image_path.suffix.lower()
        mime_type = "image/jpeg" if suffix in (".jpg", ".jpeg") else "image/png"
        payload = image_path.read_bytes()
        data = self._request(
            "POST",
            f"/edits/{quote(edit_id, safe='')}/listings/{quote(language, safe='')}/{quote(image_type, safe='')}",
            params={"uploadType": "media"},
            data=payload,
            headers={"Content-Type": mime_type},
            upload=True,
            timeout_sec=60,
        )
        return dict(data.get("image") or data)

    def commit_edit(
        self,
        edit_id: str,
        *,
        changes_not_sent_for_review: bool = False,
    ) -> dict[str, Any]:
        params = {"changesNotSentForReview": "true"} if changes_not_sent_for_review else None
        return self._request(
            "POST",
            f"/edits/{quote(edit_id, safe='')}:commit",
            params=params,
        )


def select_source_release(
    track_data: dict[str, Any],
    *,
    requested_version_code: str | None = None,
) -> dict[str, Any]:
    """Select the release to promote from a source track payload."""
    releases = [
        rel for rel in (track_data.get("releases") or []) if rel.get("versionCodes")
    ]
    if not releases:
        track_name = track_data.get("track") or "<unknown>"
        raise ValueError(f"Source track '{track_name}' has no releases with versionCodes.")

    if requested_version_code:
        wanted = str(requested_version_code).strip()
        for rel in releases:
            codes = [str(vc) for vc in rel.get("versionCodes", [])]
            if wanted in codes:
                return rel
        available = sorted(
            {str(vc) for rel in releases for vc in rel.get("versionCodes", [])},
            key=lambda x: int(x) if x.isdigit() else -1,
        )
        raise ValueError(
            f"Version code {wanted} not found on source track '{track_data.get('track')}'. "
            f"Available versionCodes on that track: {', '.join(available)}"
        )

    non_halted = [rel for rel in releases if rel.get("status") != "halted"]
    candidates = non_halted or releases

    def highest_version(rel: dict[str, Any]) -> int:
        return max(int(vc) for vc in rel.get("versionCodes", []) if str(vc).isdigit())

    return max(candidates, key=highest_version)


def load_release_notes_from_file(notes_path: Path, language: str) -> list[dict[str, str]]:
    """Read and validate localized release notes from a plain-text file (<= 500 chars)."""
    if not notes_path.is_file():
        raise ValueError(f"Release notes file not found: {notes_path}")
    text = notes_path.read_text(encoding="utf-8").strip()
    if not text:
        raise ValueError(f"Release notes file is empty: {notes_path}")
    if len(text) > MAX_RELEASE_NOTES_CHARS:
        raise ValueError(
            f"Release notes in {notes_path} exceed Google Play's {MAX_RELEASE_NOTES_CHARS}-char "
            f"limit ({len(text)} chars)."
        )
    return [{"language": language, "text": text}]


def resolve_release_notes(
    source_release: dict[str, Any],
    *,
    notes_source: str,
    notes_file: Path,
    language: str,
) -> list[dict[str, str]] | None:
    """Resolve releaseNotes list according to source_track / file / none policy."""
    if notes_source == "none":
        return None
    if notes_source == "file":
        return load_release_notes_from_file(notes_file, language)
    if notes_source == "source_track":
        existing = source_release.get("releaseNotes")
        if isinstance(existing, list) and existing:
            return existing
        if notes_file.is_file() and notes_file.read_text(encoding="utf-8").strip():
            return load_release_notes_from_file(notes_file, language)
        return None
    raise ValueError(f"Unsupported release_notes_source: {notes_source}")


def target_already_matches(
    target_track_data: dict[str, Any],
    *,
    version_codes: list[str],
    status: str,
    user_fraction: float | None,
) -> bool:
    """Return True if target track already serves the exact same versionCodes and status."""
    wanted_codes = sorted(str(vc) for vc in version_codes)
    for rel in target_track_data.get("releases") or []:
        rel_codes = sorted(str(vc) for vc in rel.get("versionCodes", []))
        if rel_codes != wanted_codes:
            continue
        if rel.get("status") != status:
            continue
        if status == "inProgress":
            existing_fraction = rel.get("userFraction")
            if existing_fraction is not None and user_fraction is not None:
                if abs(float(existing_fraction) - float(user_fraction)) < 1e-6:
                    return True
            continue
        return True
    return False


def format_tracks_markdown(package_name: str, tracks: list[dict[str, Any]]) -> str:
    """Format Play tracks as a readable Markdown table for GitHub Actions summaries."""
    lines = [
        f"### Google Play Tracks (`{package_name}`)",
        "",
        "| Track | Status | Version Codes | Release Name | User Fraction | Release Notes |",
        "| :--- | :--- | :--- | :--- | :--- | :--- |",
    ]
    if not tracks:
        lines.append("| *(no tracks returned)* | — | — | — | — | — |")
        return "\n".join(lines) + "\n"

    for track_item in tracks:
        track_name = str(track_item.get("track", ""))
        releases = track_item.get("releases") or []
        if not releases:
            lines.append(f"| `{track_name}` | *(empty)* | — | — | — | — |")
            continue
        for rel in releases:
            status = str(rel.get("status", "unknown"))
            codes = ", ".join(str(vc) for vc in rel.get("versionCodes", [])) or "—"
            name = str(rel.get("name", "")).replace("|", "\\|") or "—"
            fraction = rel.get("userFraction")
            fraction_str = f"{float(fraction) * 100:.0f}%" if fraction is not None else "100%"
            notes = rel.get("releaseNotes") or []
            langs = ", ".join(str(n.get("language", "?")) for n in notes) if notes else "—"
            lines.append(
                f"| `{track_name}` | `{status}` | `{codes}` | {name} | {fraction_str} | {langs} |"
            )
    return "\n".join(lines) + "\n"


def parse_gradle_version_code(gradle_text: str) -> int | None:
    """Extract Android defaultConfig versionCode from app/build.gradle.kts."""
    match = re.search(r"\bversionCode\s*=\s*(\d+)", gradle_text)
    return int(match.group(1)) if match else None


def read_release_sign_off_state(sign_off_path: Path) -> dict[str, str]:
    """Parse key: value fields from RELEASE_SIGN_OFF.md's RELEASE_STATE block."""
    if not sign_off_path.is_file():
        return {}
    text = sign_off_path.read_text(encoding="utf-8")
    block_match = re.search(
        r"<!--\s*RELEASE_STATE_BEGIN\s*-->(.*?)<!--\s*RELEASE_STATE_END\s*-->",
        text,
        flags=re.DOTALL,
    )
    scope = block_match.group(1) if block_match else text
    state: dict[str, str] = {}
    for line in scope.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        clean_key = key.strip()
        if clean_key and re.match(r"^[A-Z0-9_]+$", clean_key):
            state[clean_key] = value.strip()
    return state


def highest_track_version_code(track_data: dict[str, Any]) -> int | None:
    """Return the highest numeric versionCode among active (non-halted) releases on a track."""
    releases = [rel for rel in (track_data.get("releases") or []) if rel.get("versionCodes")]
    if not releases:
        return None
    non_halted = [rel for rel in releases if rel.get("status") != "halted"]
    candidates = non_halted or releases
    codes = [
        int(vc)
        for rel in candidates
        for vc in rel.get("versionCodes", [])
        if str(vc).isdigit()
    ]
    return max(codes) if codes else None


def write_step_summary(markdown: str) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY", "").strip()
    if not summary_path:
        return
    with open(summary_path, "a", encoding="utf-8") as handle:
        handle.write(markdown)
        if not markdown.endswith("\n"):
            handle.write("\n")


def write_github_output(output_path: Path | None, values: dict[str, str]) -> None:
    if output_path is None:
        return
    with open(output_path, "a", encoding="utf-8") as handle:
        for key, value in values.items():
            clean_val = str(value).replace("\r", " ").replace("\n", " ").strip()
            handle.write(f"{key}={clean_val}\n")


def check_auto_promote_preconditions(
    *,
    current_version_code: int | None,
    previous_version_code: int | None,
    sign_off_path: Path,
    require_approved: bool,
) -> str | None:
    """Validate local git versionCode bump and RELEASE_SIGN_OFF.md approval gates.

    Returns a human-readable skip reason string if promotion should be skipped cleanly,
    or None if preconditions pass and Google Play tracks should be inspected.
    """
    if previous_version_code is not None and current_version_code is not None:
        if current_version_code <= previous_version_code:
            return (
                f"versionCode did not increase ({previous_version_code} -> {current_version_code}); "
                "skipping automatic Closed testing promotion."
            )

    if require_approved:
        state = read_release_sign_off_state(sign_off_path)
        release_status = state.get("RELEASE_STATUS", "")
        if release_status != REQUIRED_APPROVAL_STATUS:
            return (
                f"{sign_off_path} has RELEASE_STATUS='{release_status or 'MISSING'}' "
                f"(auto-promote requires '{REQUIRED_APPROVAL_STATUS}')."
            )

    return None


def _emit_auto_promote_skip(package_name: str, reason: str, from_track: str, to_track: str) -> dict[str, Any]:
    summary_md = (
        f"### Google Play Auto-Promote Skipped (`{package_name}`)\n\n"
        f"- **Route:** `{from_track}` -> `{to_track}`\n"
        f"- **Reason:** {reason}\n"
    )
    print(summary_md)
    write_step_summary(summary_md)
    return {
        "promoted": False,
        "version_code": "",
        "release_name": "",
        "from_track": from_track,
        "to_track": to_track,
        "skip_reason": reason,
    }


def run_list_tracks(client: PlayPublisherClient) -> list[dict[str, Any]]:
    edit_id = client.insert_edit()
    try:
        tracks = client.list_tracks(edit_id)
    finally:
        client.delete_edit(edit_id)

    md = format_tracks_markdown(client.package_name, tracks)
    print(md)
    write_step_summary(md)
    return tracks


def extract_release_summary_version_codes(summary: dict[str, Any]) -> list[str]:
    codes: list[str] = []
    for artifact in summary.get("activeArtifacts") or []:
        vc = artifact.get("versionCode")
        if vc is not None and str(vc).strip():
            codes.append(str(vc).strip())
    return codes


def select_release_summary(
    releases: list[dict[str, Any]],
    *,
    requested_version_code: str | None = None,
) -> dict[str, Any] | None:
    if not releases:
        return None
    wanted = (requested_version_code or "").strip()
    if wanted:
        for rel in releases:
            if wanted in extract_release_summary_version_codes(rel):
                return rel
        return None

    def sort_key(rel: dict[str, Any]) -> tuple[int, str]:
        nums = [int(vc) for vc in extract_release_summary_version_codes(rel) if vc.isdigit()]
        return (max(nums) if nums else -1, str(rel.get("releaseName") or ""))

    return max(releases, key=sort_key)


def format_release_status_markdown(
    package_name: str,
    track: str,
    releases: list[dict[str, Any]],
) -> str:
    lines = [
        f"### Google Play Release Lifecycle (`{package_name}`, track `{track}`)",
        "",
        "| Track | Version Codes | Release Name | Lifecycle State |",
        "| :--- | :--- | :--- | :--- |",
    ]
    if not releases:
        lines.append(f"| `{track}` | — | — | *(no active releases)* |")
        return "\n".join(lines) + "\n"

    for rel in releases:
        rel_track = str(rel.get("track") or track)
        codes = ", ".join(extract_release_summary_version_codes(rel)) or "—"
        name = str(rel.get("releaseName") or "").replace("|", "\\|") or "—"
        state = str(rel.get("releaseLifecycleState") or "RELEASE_LIFECYCLE_STATE_UNSPECIFIED")
        lines.append(f"| `{rel_track}` | `{codes}` | {name} | `{state}` |")
    return "\n".join(lines) + "\n"


def run_release_status(
    client: PlayPublisherClient,
    *,
    track: str,
    version_code: str | None = None,
    poll_attempts: int = 1,
    poll_interval_sec: float = 30.0,
    sleep_fn: Callable[[float], None] = time.sleep,
) -> dict[str, Any]:
    """Read live Play release lifecycle states (in review / published / etc.) without an edit."""
    attempts = max(1, int(poll_attempts))
    releases: list[dict[str, Any]] = []
    matched: dict[str, Any] | None = None
    lifecycle_state = ""

    for attempt in range(1, attempts + 1):
        releases = client.list_track_releases(track)
        matched = select_release_summary(releases, requested_version_code=version_code)
        lifecycle_state = str((matched or {}).get("releaseLifecycleState") or "")
        if lifecycle_state in TERMINAL_LIFECYCLE_STATES:
            break
        if attempt < attempts:
            print(
                f"Attempt {attempt}/{attempts}: track '{track}' versionCode={version_code or 'latest'} "
                f"state={lifecycle_state or 'UNKNOWN'}; retrying in {poll_interval_sec:g}s..."
            )
            sleep_fn(poll_interval_sec)

    md = format_release_status_markdown(client.package_name, track, releases)
    print(md)
    write_step_summary(md)

    matched_codes = extract_release_summary_version_codes(matched) if matched else []
    return {
        "track": track,
        "version_code": matched_codes[0] if matched_codes else (version_code or ""),
        "release_name": str((matched or {}).get("releaseName") or ""),
        "lifecycle_state": lifecycle_state,
        "is_terminal": lifecycle_state in TERMINAL_LIFECYCLE_STATES,
        "releases": releases,
    }


def run_promote(
    client: PlayPublisherClient,
    *,
    from_track: str,
    to_track: str,
    version_code: str | None,
    status: str,
    user_fraction: float | None,
    notes_source: str,
    notes_file: Path,
    notes_language: str,
    release_name_override: str | None,
    changes_not_sent_for_review: bool,
    allow_same_version: bool,
) -> dict[str, Any]:
    if from_track == to_track:
        raise ValueError(f"from_track and to_track must be different (both are '{from_track}').")
    if status not in VALID_RELEASE_STATUSES:
        raise ValueError(f"Invalid status '{status}'. Expected one of {VALID_RELEASE_STATUSES}.")
    if status == "inProgress":
        if user_fraction is None or not (0.0 < user_fraction < 1.0):
            raise ValueError("user_fraction must be between 0 and 1 (exclusive) for status=inProgress.")
    elif user_fraction is not None:
        raise ValueError("user_fraction may only be specified when status=inProgress.")

    edit_id = client.insert_edit()
    committed = False
    try:
        all_tracks = client.list_tracks(edit_id)
        available_names = [str(t.get("track", "")) for t in all_tracks]

        try:
            source_track_data = client.get_track(edit_id, from_track)
        except PlayApiError as exc:
            if exc.status_code == 404:
                raise ValueError(
                    f"Source track '{from_track}' not found. Available tracks: {', '.join(available_names)}"
                ) from exc
            raise

        try:
            target_track_data = client.get_track(edit_id, to_track)
        except PlayApiError as exc:
            if exc.status_code == 404:
                raise ValueError(
                    f"Target track '{to_track}' not found. Available tracks: {', '.join(available_names)}"
                ) from exc
            raise

        source_release = select_source_release(
            source_track_data,
            requested_version_code=version_code,
        )
        version_codes = [str(vc) for vc in source_release.get("versionCodes", [])]

        if not allow_same_version and target_already_matches(
            target_track_data,
            version_codes=version_codes,
            status=status,
            user_fraction=user_fraction,
        ):
            raise ValueError(
                f"Target track '{to_track}' already has versionCodes {version_codes} in "
                f"status '{status}'. Pass --allow-same-version to update anyway."
            )

        release_notes = resolve_release_notes(
            source_release,
            notes_source=notes_source,
            notes_file=notes_file,
            language=notes_language,
        )
        release_name = (
            (release_name_override or "").strip()
            or str(source_release.get("name") or "").strip()
            or version_codes[0]
        )

        promoted_release: dict[str, Any] = {
            "name": release_name,
            "versionCodes": version_codes,
            "status": status,
        }
        if release_notes:
            promoted_release["releaseNotes"] = release_notes
        if status == "inProgress" and user_fraction is not None:
            promoted_release["userFraction"] = user_fraction

        releases_payload: list[dict[str, Any]] = [promoted_release]
        if status == "inProgress":
            promoted_code_set = set(version_codes)
            for existing_rel in target_track_data.get("releases") or []:
                if existing_rel.get("status") != "completed":
                    continue
                existing_codes = [str(vc) for vc in existing_rel.get("versionCodes", [])]
                if existing_codes and not (set(existing_codes) & promoted_code_set):
                    releases_payload.append(existing_rel)

        client.update_track(edit_id, to_track, releases_payload)
        client.commit_edit(
            edit_id,
            changes_not_sent_for_review=changes_not_sent_for_review,
        )
        committed = True

        notes_summary = (
            ", ".join(f"{n['language']} ({len(n['text'])} chars)" for n in release_notes)
            if release_notes
            else "none"
        )
        summary_md = (
            f"### Google Play Promotion Complete (`{client.package_name}`)\n\n"
            f"- **Source track:** `{from_track}`\n"
            f"- **Target track:** `{to_track}`\n"
            f"- **Version codes:** `{', '.join(version_codes)}`\n"
            f"- **Release name:** `{release_name}`\n"
            f"- **Status:** `{status}`"
            + (f" ({user_fraction * 100:.0f}% rollout)" if user_fraction is not None else "")
            + "\n"
            f"- **Release notes:** {notes_summary}\n"
            f"- **Changes not sent for review:** `{str(changes_not_sent_for_review).lower()}`\n"
        )
        print(summary_md)
        write_step_summary(summary_md)
        return promoted_release
    finally:
        if not committed:
            client.delete_edit(edit_id)


def run_auto_promote(
    client: PlayPublisherClient,
    *,
    from_track: str,
    to_track: str,
    current_version_code: int | None,
    previous_version_code: int | None,
    sign_off_path: Path,
    require_approved: bool,
    poll_attempts: int,
    poll_interval_sec: float,
    status: str,
    notes_source: str,
    notes_file: Path,
    notes_language: str,
    changes_not_sent_for_review: bool,
    sleep_fn: Callable[[float], None] = time.sleep,
) -> dict[str, Any]:
    """Run Stage 7 guarded automatic promotion from internal to Closed testing (alpha)."""
    skip_reason = check_auto_promote_preconditions(
        current_version_code=current_version_code,
        previous_version_code=previous_version_code,
        sign_off_path=sign_off_path,
        require_approved=require_approved,
    )
    if skip_reason:
        return _emit_auto_promote_skip(client.package_name, skip_reason, from_track, to_track)

    sign_off_state = read_release_sign_off_state(sign_off_path)
    raw_sign_off_vc = sign_off_state.get("VERSION_CODE", "").strip()
    sign_off_version_code = (
        int(raw_sign_off_vc) if raw_sign_off_vc.isdigit() and int(raw_sign_off_vc) > 0 else None
    )

    attempts = max(1, int(poll_attempts))
    source_vc: int | None = None
    target_vc: int | None = None
    candidate_vc: str | None = None

    for attempt in range(1, attempts + 1):
        edit_id = client.insert_edit()
        try:
            source_track_data = client.get_track(edit_id, from_track)
            target_track_data = client.get_track(edit_id, to_track)
        finally:
            client.delete_edit(edit_id)

        source_vc = highest_track_version_code(source_track_data)
        target_vc = highest_track_version_code(target_track_data)

        # Case A: source track already has the bumped versionCode (or manual auto-promote)
        if source_vc is not None and (current_version_code is None or source_vc >= current_version_code):
            if target_vc is not None and target_vc >= source_vc:
                return _emit_auto_promote_skip(
                    client.package_name,
                    f"Target track '{to_track}' already serves versionCode {target_vc} "
                    f"(source '{from_track}' is {source_vc}).",
                    from_track,
                    to_track,
                )
            candidate_vc = str(source_vc)
            break

        if attempt < attempts:
            print(
                f"Attempt {attempt}/{attempts}: '{from_track}' has versionCode={source_vc} "
                f"(waiting for >= {current_version_code}); retrying in {poll_interval_sec:g}s..."
            )
            sleep_fn(poll_interval_sec)

    # Case B: post-upload bump or Stage 6 sign-off trigger (source matches previous_vc or RELEASE_SIGN_OFF VERSION_CODE)
    if candidate_vc is None:
        matches_previous_bump = (
            previous_version_code is not None and source_vc is not None and source_vc >= previous_version_code
        )
        matches_sign_off_vc = (
            sign_off_version_code is not None and source_vc is not None and source_vc == sign_off_version_code
        )
        if (
            source_vc is not None
            and (matches_previous_bump or matches_sign_off_vc)
            and (target_vc is None or source_vc > target_vc)
        ):
            candidate_vc = str(source_vc)
        else:
            return _emit_auto_promote_skip(
                client.package_name,
                f"No promotable newer release on '{from_track}' yet "
                f"(from={source_vc}, to={target_vc}, expected>={current_version_code}, "
                f"sign_off={sign_off_version_code}). "
                "Re-run workflow_dispatch with mode=auto-promote once Play upload finishes.",
                from_track,
                to_track,
            )

    promoted = run_promote(
        client,
        from_track=from_track,
        to_track=to_track,
        version_code=candidate_vc,
        status=status,
        user_fraction=None,
        notes_source=notes_source,
        notes_file=notes_file,
        notes_language=notes_language,
        release_name_override="",
        changes_not_sent_for_review=changes_not_sent_for_review,
        allow_same_version=False,
    )
    promoted_codes = [str(vc) for vc in promoted.get("versionCodes", [])]
    return {
        "promoted": True,
        "version_code": promoted_codes[0] if promoted_codes else candidate_vc,
        "release_name": str(promoted.get("name") or candidate_vc),
        "from_track": from_track,
        "to_track": to_track,
        "skip_reason": "",
    }


def _parse_optional_int(raw: str, flag_name: str) -> int | None:
    cleaned = (raw or "").strip()
    if not cleaned:
        return None
    if not cleaned.isdigit():
        raise ValueError(f"{flag_name} must be a positive integer, got '{cleaned}'.")
    return int(cleaned)


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Inspect Google Play tracks and promote releases between tracks."
    )
    parser.add_argument(
        "--credentials",
        default="",
        help="Path to the Google Cloud service account JSON key file.",
    )
    parser.add_argument(
        "--package-name",
        default=DEFAULT_PACKAGE_NAME,
        help=f"Android applicationId (default: {DEFAULT_PACKAGE_NAME}).",
    )
    parser.add_argument(
        "--github-output",
        default="",
        help="Optional path (e.g. $GITHUB_OUTPUT) to emit promoted/version_code outputs.",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("list-tracks", help="List all tracks and active releases (read-only).")

    status_parser = subparsers.add_parser(
        "release-status",
        help="Read release lifecycle states (in review / published) for a Play track (read-only).",
    )
    status_parser.add_argument("--track", default="alpha")
    status_parser.add_argument("--version-code", default="")
    status_parser.add_argument("--poll-attempts", type=int, default=1)
    status_parser.add_argument("--poll-interval-sec", type=float, default=30.0)

    promote_parser = subparsers.add_parser(
        "promote",
        help="Promote an existing release from one Play track to another.",
    )
    promote_parser.add_argument("--from-track", default="internal")
    promote_parser.add_argument("--to-track", default="alpha")
    promote_parser.add_argument("--version-code", default="")
    promote_parser.add_argument(
        "--status",
        choices=VALID_RELEASE_STATUSES,
        default="completed",
    )
    promote_parser.add_argument("--user-fraction", default="")
    promote_parser.add_argument(
        "--release-notes-source",
        choices=VALID_NOTES_SOURCES,
        default="source_track",
    )
    promote_parser.add_argument("--release-notes-file", default=DEFAULT_RELEASE_NOTES_FILE)
    promote_parser.add_argument("--release-notes-language", default=DEFAULT_RELEASE_NOTES_LANGUAGE)
    promote_parser.add_argument("--release-name", default="")
    promote_parser.add_argument("--changes-not-sent-for-review", action="store_true")
    promote_parser.add_argument("--allow-same-version", action="store_true")

    auto_parser = subparsers.add_parser(
        "auto-promote",
        help="Stage 7 guarded promotion from internal to Closed testing after APPROVED_FOR_PLAY_STORE.",
    )
    auto_parser.add_argument("--from-track", default="internal")
    auto_parser.add_argument("--to-track", default="alpha")
    auto_parser.add_argument("--current-version-code", default="")
    auto_parser.add_argument("--previous-version-code", default="")
    auto_parser.add_argument("--sign-off-file", default=DEFAULT_SIGN_OFF_FILE)
    auto_parser.add_argument("--no-require-approved", action="store_true")
    auto_parser.add_argument("--poll-attempts", type=int, default=DEFAULT_POLL_ATTEMPTS)
    auto_parser.add_argument("--poll-interval-sec", type=float, default=DEFAULT_POLL_INTERVAL_SEC)
    auto_parser.add_argument(
        "--status",
        choices=("completed", "draft"),
        default="completed",
    )
    auto_parser.add_argument(
        "--release-notes-source",
        choices=VALID_NOTES_SOURCES,
        default="source_track",
    )
    auto_parser.add_argument("--release-notes-file", default=DEFAULT_RELEASE_NOTES_FILE)
    auto_parser.add_argument("--release-notes-language", default=DEFAULT_RELEASE_NOTES_LANGUAGE)
    auto_parser.add_argument("--changes-not-sent-for-review", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    output_path = Path(args.github_output) if (args.github_output or "").strip() else None

    try:
        if args.command == "auto-promote":
            current_vc = _parse_optional_int(args.current_version_code, "--current-version-code")
            previous_vc = _parse_optional_int(args.previous_version_code, "--previous-version-code")
            sign_off_path = Path(args.sign_off_file)
            require_approved = not bool(args.no_require_approved)
            from_track = args.from_track.strip()
            to_track = args.to_track.strip()

            # Check local git bump + RELEASE_SIGN_OFF.md gates before authenticating with Play API.
            early_skip = check_auto_promote_preconditions(
                current_version_code=current_vc,
                previous_version_code=previous_vc,
                sign_off_path=sign_off_path,
                require_approved=require_approved,
            )
            if early_skip:
                result = _emit_auto_promote_skip(args.package_name, early_skip, from_track, to_track)
                write_github_output(
                    output_path,
                    {
                        "promoted": "false",
                        "version_code": "",
                        "release_name": "",
                        "from_track": from_track,
                        "to_track": to_track,
                        "skip_reason": result["skip_reason"],
                    },
                )
                return 0

        raw_creds = (args.credentials or "").strip()
        if not raw_creds:
            raise ValueError(
                "Missing --credentials path (ensure repository secret PLAY_SERVICE_ACCOUNT_JSON is configured)."
            )
        creds_path = Path(raw_creds)
        client_email = validate_service_account_file(creds_path)
        print(f"Authenticated as service account: {client_email}")
        session = build_authorized_session(creds_path)
        client = PlayPublisherClient(args.package_name, session=session)

        if args.command == "list-tracks":
            run_list_tracks(client)
            write_github_output(output_path, {"promoted": "false"})
            return 0

        if args.command == "release-status":
            raw_vc = (args.version_code or "").strip()
            version_code = raw_vc if raw_vc else None
            if version_code is not None and not version_code.isdigit():
                raise ValueError(f"--version-code must be numeric, got '{version_code}'.")
            status_result = run_release_status(
                client,
                track=args.track.strip() or "alpha",
                version_code=version_code,
                poll_attempts=args.poll_attempts,
                poll_interval_sec=args.poll_interval_sec,
            )
            write_github_output(
                output_path,
                {
                    "promoted": "false",
                    "track": status_result["track"],
                    "version_code": status_result["version_code"],
                    "release_name": status_result["release_name"],
                    "lifecycle_state": status_result["lifecycle_state"],
                    "is_terminal": "true" if status_result["is_terminal"] else "false",
                },
            )
            return 0

        if args.command == "auto-promote":
            result = run_auto_promote(
                client,
                from_track=args.from_track.strip(),
                to_track=args.to_track.strip(),
                current_version_code=current_vc,
                previous_version_code=previous_vc,
                sign_off_path=sign_off_path,
                require_approved=require_approved,
                poll_attempts=args.poll_attempts,
                poll_interval_sec=args.poll_interval_sec,
                status=args.status,
                notes_source=args.release_notes_source,
                notes_file=Path(args.release_notes_file),
                notes_language=args.release_notes_language.strip() or DEFAULT_RELEASE_NOTES_LANGUAGE,
                changes_not_sent_for_review=bool(args.changes_not_sent_for_review),
            )
            write_github_output(
                output_path,
                {
                    "promoted": "true" if result["promoted"] else "false",
                    "version_code": result["version_code"],
                    "release_name": result["release_name"],
                    "from_track": result["from_track"],
                    "to_track": result["to_track"],
                    "skip_reason": result["skip_reason"],
                },
            )
            return 0

        raw_fraction = (args.user_fraction or "").strip()
        user_fraction = float(raw_fraction) if raw_fraction else None
        raw_vc = (args.version_code or "").strip()
        version_code = raw_vc if raw_vc else None
        if version_code is not None and not version_code.isdigit():
            raise ValueError(f"--version-code must be numeric, got '{version_code}'.")

        from_track = args.from_track.strip()
        to_track = args.to_track.strip()
        promoted = run_promote(
            client,
            from_track=from_track,
            to_track=to_track,
            version_code=version_code,
            status=args.status,
            user_fraction=user_fraction,
            notes_source=args.release_notes_source,
            notes_file=Path(args.release_notes_file),
            notes_language=args.release_notes_language.strip() or DEFAULT_RELEASE_NOTES_LANGUAGE,
            release_name_override=args.release_name,
            changes_not_sent_for_review=bool(args.changes_not_sent_for_review),
            allow_same_version=bool(args.allow_same_version),
        )
        promoted_codes = [str(vc) for vc in promoted.get("versionCodes", [])]
        write_github_output(
            output_path,
            {
                "promoted": "true",
                "version_code": promoted_codes[0] if promoted_codes else "",
                "release_name": str(promoted.get("name") or ""),
                "from_track": from_track,
                "to_track": to_track,
                "skip_reason": "",
            },
        )
        return 0
    except handled_cli_exceptions() as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


def handled_cli_exceptions() -> tuple[type[BaseException], ...]:
    """Return handled CLI error types including requests.RequestException when available."""
    exc_types: list[type[BaseException]] = [ValueError, PlayApiError]
    try:
        import requests  # type: ignore[import-not-found]

        req_exc = getattr(requests, "RequestException", None)
        if isinstance(req_exc, type) and issubclass(req_exc, BaseException):
            exc_types.append(req_exc)
    except ImportError:
        pass
    return tuple(exc_types)


if __name__ == "__main__":
    raise SystemExit(main())
