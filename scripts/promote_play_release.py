#!/usr/bin/env python3
"""Inspect Google Play tracks and promote releases between tracks via androidpublisher v3.

Supports read-only track inspection (`list-tracks`) and track-to-track release promotion
(`promote`, e.g. `internal` -> `alpha` or a custom closed testing track) without needing
the Android upload signing keystore.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any
from urllib.parse import quote

DEFAULT_PACKAGE_NAME = "com.aistudio.wemiswitch.kxpqzr"
DEFAULT_RELEASE_NOTES_FILE = "play_store_assets/RELEASE_NOTES.txt"
DEFAULT_RELEASE_NOTES_LANGUAGE = "en-US"
PLAY_API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
PLAY_OAUTH_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
MAX_RELEASE_NOTES_CHARS = 500
VALID_RELEASE_STATUSES = ("completed", "draft", "inProgress")
VALID_NOTES_SOURCES = ("source_track", "file", "none")


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
            "with app permissions ('View app information' and 'Release apps to testing tracks'). "
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
    """Minimal Google Play Android Publisher API v3 client for track listing & promotion."""

    def __init__(self, package_name: str, session: Any, timeout_sec: int = 30) -> None:
        self.package_name = package_name
        self.session = session
        self.timeout_sec = timeout_sec
        self.base_url = f"{PLAY_API_BASE}/{quote(package_name, safe='')}"

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, str] | None = None,
        json_body: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        url = f"{self.base_url}{path}"
        response = self.session.request(
            method,
            url,
            params=params,
            json=json_body,
            timeout=self.timeout_sec,
        )
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


def write_step_summary(markdown: str) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY", "").strip()
    if not summary_path:
        return
    with open(summary_path, "a", encoding="utf-8") as handle:
        handle.write(markdown)
        if not markdown.endswith("\n"):
            handle.write("\n")


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

        client.update_track(edit_id, to_track, [promoted_release])
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


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Inspect Google Play tracks and promote releases between tracks."
    )
    parser.add_argument(
        "--credentials",
        required=True,
        help="Path to the Google Cloud service account JSON key file.",
    )
    parser.add_argument(
        "--package-name",
        default=DEFAULT_PACKAGE_NAME,
        help=f"Android applicationId (default: {DEFAULT_PACKAGE_NAME}).",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("list-tracks", help="List all tracks and active releases (read-only).")

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
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    creds_path = Path(args.credentials)
    try:
        client_email = validate_service_account_file(creds_path)
        print(f"Authenticated as service account: {client_email}")
        session = build_authorized_session(creds_path)
        client = PlayPublisherClient(args.package_name, session=session)

        if args.command == "list-tracks":
            run_list_tracks(client)
            return 0

        raw_fraction = (args.user_fraction or "").strip()
        user_fraction = float(raw_fraction) if raw_fraction else None
        raw_vc = (args.version_code or "").strip()
        version_code = raw_vc if raw_vc else None
        if version_code is not None and not version_code.isdigit():
            raise ValueError(f"--version-code must be numeric, got '{version_code}'.")

        run_promote(
            client,
            from_track=args.from_track.strip(),
            to_track=args.to_track.strip(),
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
        return 0
    except (ValueError, PlayApiError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
