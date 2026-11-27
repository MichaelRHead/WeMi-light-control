#!/usr/bin/env python3
"""Validate and upload a signed Android App Bundle (.aab) to Google Play via androidpublisher v3.

Reuses PlayPublisherClient from scripts/promote_play_release.py so zero additional Python
dependencies are introduced beyond scripts/requirements-play.txt.
"""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import sys
import zipfile
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from promote_play_release import (  # noqa: E402
    DEFAULT_PACKAGE_NAME,
    DEFAULT_RELEASE_NOTES_FILE,
    DEFAULT_RELEASE_NOTES_LANGUAGE,
    VALID_RELEASE_STATUSES,
    PlayPublisherClient,
    build_authorized_session,
    handled_cli_exceptions,
    load_release_notes_from_file,
    parse_gradle_version_code,
    parse_gradle_version_name,
    validate_service_account_file,
    write_github_output,
    write_step_summary,
)

DEFAULT_BUNDLE_PATH = "app/build/outputs/bundle/release/app-release.aab"
DEFAULT_GRADLE_FILE = "app/build.gradle.kts"
DEFAULT_TRACK = "internal"
VALID_UPLOAD_NOTES_SOURCES = ("file", "none")
FORBIDDEN_BINARY_EXTENSIONS = (".so", ".ttf", ".otf")
JAR_SIGNATURE_EXTENSIONS = (".rsa", ".dsa", ".ec")
REQUIRED_AAB_MANIFEST_ENTRY = "base/manifest/AndroidManifest.xml"
PROGUARD_MAP_ENTRY = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"


@dataclasses.dataclass(frozen=True)
class BundleInspection:
    bundle_path: Path
    size_bytes: int
    sha256: str
    entry_count: int
    signature_files: tuple[str, ...]
    has_proguard_map: bool
    forbidden_binaries: tuple[str, ...]


def inspect_bundle_archive(
    bundle_path: Path,
    *,
    allow_native_or_fonts: bool = False,
) -> BundleInspection:
    """Validate that bundle_path is a signed, pure Kotlin/DEX Android App Bundle."""
    if not bundle_path.is_file():
        raise ValueError(f"App Bundle file not found: {bundle_path}")

    size_bytes = bundle_path.stat().st_size
    if size_bytes <= 0:
        raise ValueError(f"App Bundle file is empty: {bundle_path}")

    digest = hashlib.sha256()
    with bundle_path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            digest.update(chunk)
    sha256 = digest.hexdigest()

    try:
        with zipfile.ZipFile(bundle_path, "r") as zf:
            names = zf.namelist()
    except zipfile.BadZipFile as exc:
        raise ValueError(f"App Bundle is not a valid ZIP/AAB archive: {bundle_path}") from exc

    if REQUIRED_AAB_MANIFEST_ENTRY not in names:
        raise ValueError(
            f"App Bundle is missing required entry '{REQUIRED_AAB_MANIFEST_ENTRY}': {bundle_path}"
        )

    sig_files = tuple(
        sorted(
            name
            for name in names
            if name.startswith("META-INF/") and name.lower().endswith(JAR_SIGNATURE_EXTENSIONS)
        )
    )
    if not sig_files:
        raise ValueError(
            f"App Bundle appears unsigned (no META-INF/*.RSA|DSA|EC signature found): {bundle_path}"
        )

    forbidden = tuple(
        sorted(name for name in names if name.lower().endswith(FORBIDDEN_BINARY_EXTENSIONS))
    )
    if forbidden and not allow_native_or_fonts:
        preview = ", ".join(forbidden[:5])
        raise ValueError(
            f"App Bundle contains disallowed native/font binaries ({len(forbidden)}): {preview}"
        )

    return BundleInspection(
        bundle_path=bundle_path,
        size_bytes=size_bytes,
        sha256=sha256,
        entry_count=len(names),
        signature_files=sig_files,
        has_proguard_map=PROGUARD_MAP_ENTRY in names,
        forbidden_binaries=forbidden,
    )


def validate_upload_options(
    *,
    track: str,
    status: str,
    user_fraction: float | None,
    notes_source: str,
) -> None:
    """Validate Play track, release status, rollout fraction, and notes policy."""
    if not track.strip():
        raise ValueError("--track must not be empty.")
    if status not in VALID_RELEASE_STATUSES:
        raise ValueError(
            f"Invalid --status '{status}'. Expected one of: {', '.join(VALID_RELEASE_STATUSES)}"
        )
    if status == "inProgress":
        if user_fraction is None or not (0.0 < user_fraction < 1.0):
            raise ValueError(
                "--user-fraction must be strictly between 0 and 1 when --status=inProgress."
            )
    elif user_fraction is not None:
        raise ValueError("--user-fraction is only valid when --status=inProgress.")
    if notes_source not in VALID_UPLOAD_NOTES_SOURCES:
        raise ValueError(
            f"Invalid --release-notes-source '{notes_source}'. "
            f"Expected one of: {', '.join(VALID_UPLOAD_NOTES_SOURCES)}"
        )


def resolve_gradle_metadata(gradle_path: Path | None) -> tuple[int | None, str | None]:
    """Read (versionCode, versionName) from app/build.gradle.kts if available."""
    if gradle_path is None or not gradle_path.is_file():
        return (None, None)
    text = gradle_path.read_text(encoding="utf-8")
    return (parse_gradle_version_code(text), parse_gradle_version_name(text))


def run_upload_bundle(
    client: PlayPublisherClient | None,
    *,
    bundle_path: Path,
    track: str,
    status: str = "completed",
    user_fraction: float | None = None,
    expected_version_code: int | None = None,
    release_name: str | None = None,
    notes_source: str = "file",
    notes_file: Path = Path(DEFAULT_RELEASE_NOTES_FILE),
    notes_language: str = DEFAULT_RELEASE_NOTES_LANGUAGE,
    changes_not_sent_for_review: bool = False,
    allow_native_or_fonts: bool = False,
    dry_run: bool = False,
) -> dict[str, Any]:
    """Validate and upload a signed .aab bundle to a Google Play track."""
    clean_track = track.strip()
    validate_upload_options(
        track=clean_track,
        status=status,
        user_fraction=user_fraction,
        notes_source=notes_source,
    )

    inspection = inspect_bundle_archive(
        bundle_path,
        allow_native_or_fonts=allow_native_or_fonts,
    )

    release_notes: list[dict[str, str]] | None = None
    if notes_source == "file":
        release_notes = load_release_notes_from_file(notes_file, notes_language)

    resolved_name = (release_name or "").strip() or None
    if dry_run:
        summary_lines = [
            "### Android App Bundle Dry-Run Validation (No Play Upload)",
            "",
            f"- **Bundle:** `{inspection.bundle_path}` (`{inspection.size_bytes:,}` bytes)",
            f"- **SHA-256:** `{inspection.sha256}`",
            f"- **Signature Entries:** `{', '.join(inspection.signature_files)}`",
            f"- **Embedded R8/ProGuard Map:** `{'yes' if inspection.has_proguard_map else 'no'}`",
            f"- **Disallowed `.so`/`.ttf`/`.otf` Binaries:** `{len(inspection.forbidden_binaries)}`",
            f"- **Target Track / Status (if uploaded):** `{clean_track}` / `{status}`",
        ]
        if expected_version_code is not None:
            summary_lines.append(f"- **Expected `versionCode`:** `{expected_version_code}`")
        if resolved_name:
            summary_lines.append(f"- **Release Name:** `{resolved_name}`")
        if release_notes:
            summary_lines.append(
                f"- **Release Notes:** `{notes_language}` ({len(release_notes[0]['text'])} chars)"
            )
        summary = "\n".join(summary_lines) + "\n"
        print(summary)
        write_step_summary(summary)
        return {
            "uploaded": False,
            "dry_run": True,
            "version_code": str(expected_version_code) if expected_version_code is not None else "",
            "track": clean_track,
            "status": status,
            "release_name": resolved_name or "",
            "sha256": inspection.sha256,
            "has_proguard_map": inspection.has_proguard_map,
        }

    if client is None:
        raise ValueError("PlayPublisherClient is required unless --dry-run is enabled.")

    edit_id = client.insert_edit()
    try:
        bundle_resp = client.upload_bundle(edit_id, bundle_path)
        raw_vc = bundle_resp.get("versionCode")
        if raw_vc is None or not str(raw_vc).isdigit():
            raise ValueError(
                f"Google Play bundle upload response missing numeric versionCode: {bundle_resp}"
            )
        uploaded_vc = int(str(raw_vc))

        if expected_version_code is not None and uploaded_vc != expected_version_code:
            raise ValueError(
                f"Uploaded bundle versionCode ({uploaded_vc}) does not match expected "
                f"versionCode ({expected_version_code}). Aborting edit before track update."
            )

        release_payload: dict[str, Any] = {
            "versionCodes": [str(uploaded_vc)],
            "status": status,
        }
        if resolved_name:
            release_payload["name"] = resolved_name
        if status == "inProgress" and user_fraction is not None:
            release_payload["userFraction"] = user_fraction
        if release_notes:
            release_payload["releaseNotes"] = release_notes

        client.update_track(edit_id, clean_track, [release_payload])
        client.commit_edit(
            edit_id,
            changes_not_sent_for_review=changes_not_sent_for_review,
        )
    except Exception:
        client.delete_edit(edit_id)
        raise

    remote_sha256 = str(bundle_resp.get("sha256") or inspection.sha256)
    summary_lines = [
        f"### Google Play Bundle Uploaded (`{client.package_name}`)",
        "",
        f"- **Uploaded `versionCode`:** `{uploaded_vc}`",
        f"- **Track / Status:** `{clean_track}` / `{status}`",
        f"- **Release Name:** `{resolved_name or 'default'}`",
        f"- **Bundle SHA-256:** `{remote_sha256}`",
        f"- **Embedded R8/ProGuard Map:** `{'yes' if inspection.has_proguard_map else 'no'}`",
        f"- **Release Notes:** `{notes_language if release_notes else 'none'}`",
    ]
    summary = "\n".join(summary_lines) + "\n"
    print(summary)
    write_step_summary(summary)

    return {
        "uploaded": True,
        "dry_run": False,
        "version_code": str(uploaded_vc),
        "track": clean_track,
        "status": status,
        "release_name": resolved_name or "",
        "sha256": remote_sha256,
        "has_proguard_map": inspection.has_proguard_map,
    }


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate and upload a signed Android App Bundle (.aab) to Google Play."
    )
    parser.add_argument(
        "--bundle",
        default=DEFAULT_BUNDLE_PATH,
        help=f"Path to signed .aab file (default: {DEFAULT_BUNDLE_PATH})",
    )
    parser.add_argument(
        "--credentials",
        default="",
        help="Path to Google Play service account JSON key file (omitted when --dry-run).",
    )
    parser.add_argument(
        "--package-name",
        default=DEFAULT_PACKAGE_NAME,
        help=f"Android applicationId (default: {DEFAULT_PACKAGE_NAME})",
    )
    parser.add_argument(
        "--gradle-file",
        default=DEFAULT_GRADLE_FILE,
        help=f"Optional Gradle script for versionCode/versionName defaults (default: {DEFAULT_GRADLE_FILE})",
    )
    parser.add_argument(
        "--expected-version-code",
        default="",
        help="Expected versionCode integer override (set '0' to disable check; blank reads --gradle-file).",
    )
    parser.add_argument(
        "--track",
        default=DEFAULT_TRACK,
        help=f"Target Google Play track (default: {DEFAULT_TRACK})",
    )
    parser.add_argument(
        "--status",
        choices=VALID_RELEASE_STATUSES,
        default="completed",
        help="Release status on target track (default: completed).",
    )
    parser.add_argument(
        "--user-fraction",
        default="",
        help="Staged rollout fraction between 0 and 1 (only valid when --status=inProgress).",
    )
    parser.add_argument(
        "--release-name",
        default="",
        help="Optional Play release name override (blank defaults to versionName from --gradle-file).",
    )
    parser.add_argument(
        "--release-notes-source",
        choices=VALID_UPLOAD_NOTES_SOURCES,
        default="file",
        help="Release notes source: file or none (default: file).",
    )
    parser.add_argument(
        "--release-notes-file",
        default=DEFAULT_RELEASE_NOTES_FILE,
        help=f"Plain-text release notes file <= 500 chars (default: {DEFAULT_RELEASE_NOTES_FILE}).",
    )
    parser.add_argument(
        "--release-notes-language",
        default=DEFAULT_RELEASE_NOTES_LANGUAGE,
        help=f"BCP-47 locale for release notes (default: {DEFAULT_RELEASE_NOTES_LANGUAGE}).",
    )
    parser.add_argument(
        "--changes-not-sent-for-review",
        action="store_true",
        help="Commit edit with changesNotSentForReview=true.",
    )
    parser.add_argument(
        "--allow-native-or-fonts",
        action="store_true",
        help="Disable the pure Kotlin/DEX check for .so/.ttf/.otf files.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate bundle signature, structure, and notes locally without contacting Google Play.",
    )
    parser.add_argument(
        "--github-output",
        default="",
        help="Optional path to $GITHUB_OUTPUT file for machine-readable outputs.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    output_path = Path(args.github_output) if (args.github_output or "").strip() else None

    try:
        gradle_path = Path(args.gradle_file) if (args.gradle_file or "").strip() else None
        gradle_vc, gradle_vname = resolve_gradle_metadata(gradle_path)

        raw_expected_vc = (args.expected_version_code or "").strip()
        if raw_expected_vc == "0":
            expected_vc: int | None = None
        elif raw_expected_vc:
            if not raw_expected_vc.isdigit():
                raise ValueError(
                    f"--expected-version-code must be a positive integer (or 0), got '{raw_expected_vc}'."
                )
            expected_vc = int(raw_expected_vc)
        else:
            expected_vc = gradle_vc

        release_name = (args.release_name or "").strip() or gradle_vname

        raw_fraction = (args.user_fraction or "").strip()
        user_fraction = float(raw_fraction) if raw_fraction else None

        client: PlayPublisherClient | None = None
        if not args.dry_run:
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

        result = run_upload_bundle(
            client,
            bundle_path=Path(args.bundle),
            track=args.track,
            status=args.status,
            user_fraction=user_fraction,
            expected_version_code=expected_vc,
            release_name=release_name,
            notes_source=args.release_notes_source,
            notes_file=Path(args.release_notes_file),
            notes_language=args.release_notes_language.strip() or DEFAULT_RELEASE_NOTES_LANGUAGE,
            changes_not_sent_for_review=bool(args.changes_not_sent_for_review),
            allow_native_or_fonts=bool(args.allow_native_or_fonts),
            dry_run=bool(args.dry_run),
        )
        write_github_output(
            output_path,
            {
                "uploaded": "true" if result["uploaded"] else "false",
                "version_code": result["version_code"],
                "track": result["track"],
                "status": result["status"],
                "release_name": result["release_name"],
                "sha256": result["sha256"],
                "has_proguard_map": "true" if result["has_proguard_map"] else "false",
            },
        )
        return 0
    except handled_cli_exceptions() as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
