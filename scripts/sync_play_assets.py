#!/usr/bin/env python3
"""Inspect and idempotently sync play_store_assets/ to Google Play via androidpublisher v3.

Syncs what the Google Play Developer API supports:
  - Store listing copy (title, shortDescription, fullDescription) from play_store_assets/STORE_LISTING.md
  - Graphic assets across all 7 Play Console image types:
      * icon                 -> play_store_assets/play_store_icon_512x512.png
      * featureGraphic       -> play_store_assets/feature_graphic_1024x500.png
      * tvBanner             -> play_store_assets/tv/tv_banner_1280x720.png
      * phoneScreenshots     -> play_store_assets/screenshots/phone/*.png
      * sevenInchScreenshots -> play_store_assets/screenshots/tablet_7_inch/*.png
      * tenInchScreenshots   -> play_store_assets/screenshots/tablet_10_inch/*.png
      * tvScreenshots        -> play_store_assets/screenshots/android_tv/*.png

Uses SHA-256 / SHA-1 comparison so unchanged images and unchanged listing fields
produce zero writes and zero unnecessary Play Store edits.
"""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import re
import sys
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from promote_play_release import (  # noqa: E402
    DEFAULT_PACKAGE_NAME,
    PlayApiError,
    PlayPublisherClient,
    build_authorized_session,
    validate_service_account_file,
    write_github_output,
    write_step_summary,
)

DEFAULT_ASSETS_DIR = "play_store_assets"
DEFAULT_LISTING_FILE = "play_store_assets/STORE_LISTING.md"
DEFAULT_LANGUAGE = "en-US"

MAX_TITLE_CHARS = 30
MAX_SHORT_DESCRIPTION_CHARS = 80
MAX_FULL_DESCRIPTION_CHARS = 4000
MAX_SCREENSHOTS_PER_TYPE = 8
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
TRADEMARK_GUARD_RE = re.compile(r"\bWeMo\b|\bWemo\b")


@dataclasses.dataclass(frozen=True)
class ImageSlotSpec:
    image_type: str
    label: str
    relative_pattern: str
    is_single: bool


IMAGE_TYPE_SPECS: tuple[ImageSlotSpec, ...] = (
    ImageSlotSpec("icon", "App Icon (512x512)", "play_store_icon_512x512.png", True),
    ImageSlotSpec("featureGraphic", "Feature Graphic (1024x500)", "feature_graphic_1024x500.png", True),
    ImageSlotSpec("tvBanner", "Android TV Banner (1280x720)", "tv/tv_banner_1280x720.png", True),
    ImageSlotSpec("phoneScreenshots", "Phone Screenshots", "screenshots/phone/*.png", False),
    ImageSlotSpec("sevenInchScreenshots", "7-Inch Tablet Screenshots", "screenshots/tablet_7_inch/*.png", False),
    ImageSlotSpec("tenInchScreenshots", "10-Inch Tablet Screenshots", "screenshots/tablet_10_inch/*.png", False),
    ImageSlotSpec("tvScreenshots", "Android TV Screenshots", "screenshots/android_tv/*.png", False),
)


@dataclasses.dataclass(frozen=True)
class StoreListingSpec:
    language: str
    title: str
    short_description: str
    full_description: str


@dataclasses.dataclass(frozen=True)
class LocalImageAsset:
    path: Path
    sha256: str
    sha1: str
    size_bytes: int


def markdown_to_play_plain_text(markdown: str) -> str:
    """Convert Markdown formatting in STORE_LISTING.md into clean Play Store plain text."""
    lines: list[str] = []
    for raw_line in markdown.replace("\r\n", "\n").splitlines():
        line = raw_line.rstrip()
        line = re.sub(r"^#{1,6}\s+", "", line)
        line = re.sub(r"^\s*[-*]\s+", "• ", line)
        line = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", line)
        line = re.sub(r"\*\*([^*]+)\*\*", r"\1", line)
        line = re.sub(r"`([^`]+)`", r"\1", line)
        lines.append(line.rstrip())
    text = "\n".join(lines).strip()
    return re.sub(r"\n{3,}", "\n\n", text)


def normalize_listing_text(value: str | None) -> str:
    """Normalize line endings and trailing whitespace for stable listing comparisons."""
    if not value:
        return ""
    lines = [line.rstrip() for line in value.replace("\r\n", "\n").splitlines()]
    return "\n".join(lines).strip()


def parse_store_listing_markdown(listing_path: Path, language: str = DEFAULT_LANGUAGE) -> StoreListingSpec:
    """Parse and validate title, shortDescription, and fullDescription from STORE_LISTING.md."""
    if not listing_path.is_file():
        raise ValueError(f"Store listing markdown file not found: {listing_path}")

    raw_text = listing_path.read_text(encoding="utf-8")
    if TRADEMARK_GUARD_RE.search(raw_text):
        raise ValueError(f"Trademark boundary check failed in {listing_path}; aborting Play Store sync.")

    title_match = re.search(r"\*\*App Title[^:]*:\*\*\s*`([^`]+)`", raw_text)
    if not title_match:
        raise ValueError(f"Could not find '**App Title...:** `<title>`' in {listing_path}.")
    title = normalize_listing_text(title_match.group(1))

    short_match = re.search(r"^##\s+Short Description[^\n]*\n+([^\n]+)", raw_text, flags=re.MULTILINE)
    if not short_match:
        raise ValueError(f"Could not find '## Short Description' body in {listing_path}.")
    short_description = normalize_listing_text(markdown_to_play_plain_text(short_match.group(1)))

    full_match = re.search(
        r"^##\s+Full Description[^\n]*\n(.*?)(?=^##\s+|\Z)",
        raw_text,
        flags=re.MULTILINE | re.DOTALL,
    )
    if not full_match:
        raise ValueError(f"Could not find '## Full Description' section in {listing_path}.")
    full_description = normalize_listing_text(markdown_to_play_plain_text(full_match.group(1)))

    if not title or len(title) > MAX_TITLE_CHARS:
        raise ValueError(
            f"Store listing title must be 1..{MAX_TITLE_CHARS} chars (got {len(title)}: {title!r})."
        )
    if not short_description or len(short_description) > MAX_SHORT_DESCRIPTION_CHARS:
        raise ValueError(
            f"Store listing shortDescription must be 1..{MAX_SHORT_DESCRIPTION_CHARS} chars "
            f"(got {len(short_description)})."
        )
    if not full_description or len(full_description) > MAX_FULL_DESCRIPTION_CHARS:
        raise ValueError(
            f"Store listing fullDescription must be 1..{MAX_FULL_DESCRIPTION_CHARS} chars "
            f"(got {len(full_description)}). Trim '## Full Description' in {listing_path}."
        )

    return StoreListingSpec(
        language=language,
        title=title,
        short_description=short_description,
        full_description=full_description,
    )


def _hash_image_file(image_path: Path) -> LocalImageAsset:
    raw = image_path.read_bytes()
    if not raw:
        raise ValueError(f"Image file is empty: {image_path}")
    if image_path.suffix.lower() == ".png" and not raw.startswith(PNG_SIGNATURE):
        raise ValueError(f"Invalid PNG header in {image_path}")
    return LocalImageAsset(
        path=image_path,
        sha256=hashlib.sha256(raw).hexdigest().lower(),
        sha1=hashlib.sha1(raw).hexdigest().lower(),
        size_bytes=len(raw),
    )


def discover_local_image_assets(
    assets_dir: Path,
    selected_types: set[str] | None = None,
) -> dict[str, list[LocalImageAsset]]:
    """Resolve and hash canonical Play Console image assets under play_store_assets/."""
    if not assets_dir.is_dir():
        raise ValueError(f"Play Store assets directory not found: {assets_dir}")

    discovered: dict[str, list[LocalImageAsset]] = {}
    for spec in IMAGE_TYPE_SPECS:
        if selected_types is not None and spec.image_type not in selected_types:
            continue

        if spec.is_single:
            target = assets_dir / spec.relative_pattern
            if not target.is_file():
                raise ValueError(f"Required Play Store image for '{spec.image_type}' not found: {target}")
            discovered[spec.image_type] = [_hash_image_file(target)]
        else:
            matches = sorted(p for p in assets_dir.glob(spec.relative_pattern) if p.is_file())
            if not matches:
                raise ValueError(
                    f"No Play Store screenshots found for '{spec.image_type}' matching {assets_dir / spec.relative_pattern}"
                )
            if len(matches) > MAX_SCREENSHOTS_PER_TYPE:
                raise ValueError(
                    f"Too many screenshots for '{spec.image_type}': found {len(matches)}, "
                    f"Google Play allows at most {MAX_SCREENSHOTS_PER_TYPE}."
                )
            discovered[spec.image_type] = [_hash_image_file(path) for path in matches]

    return discovered


def listing_changed_fields(
    local_listing: StoreListingSpec,
    remote_listing: dict[str, Any] | None,
) -> list[str]:
    """Return list of Store Listing text fields that differ from Google Play."""
    remote = remote_listing or {}
    changed: list[str] = []
    checks = (
        ("title", local_listing.title, remote.get("title")),
        ("shortDescription", local_listing.short_description, remote.get("shortDescription")),
        ("fullDescription", local_listing.full_description, remote.get("fullDescription")),
    )
    for field_name, local_val, remote_val in checks:
        if normalize_listing_text(local_val) != normalize_listing_text(str(remote_val or "")):
            changed.append(field_name)
    return changed


def images_match_remote(
    local_assets: list[LocalImageAsset],
    remote_images: list[dict[str, Any]],
) -> bool:
    """Return True if remote Play images match local files in exact order and hash."""
    if len(local_assets) != len(remote_images):
        return False
    for local_item, remote_item in zip(local_assets, remote_images):
        remote_sha256 = str(remote_item.get("sha256") or "").strip().lower()
        remote_sha1 = str(remote_item.get("sha1") or "").strip().lower()
        if remote_sha256:
            if local_item.sha256 != remote_sha256:
                return False
        elif remote_sha1:
            if local_item.sha1 != remote_sha1:
                return False
        else:
            return False
    return True


def _fetch_remote_listing_or_none(
    client: PlayPublisherClient,
    edit_id: str,
    language: str,
) -> dict[str, Any] | None:
    try:
        return client.get_listing(edit_id, language)
    except PlayApiError as exc:
        if exc.status_code == 404:
            return None
        raise


def _fetch_remote_images_or_empty(
    client: PlayPublisherClient,
    edit_id: str,
    language: str,
    image_type: str,
) -> list[dict[str, Any]]:
    try:
        return client.list_images(edit_id, language, image_type)
    except PlayApiError as exc:
        if exc.status_code == 404:
            return []
        raise


def _short_hash_summary(local_assets: list[LocalImageAsset], remote_images: list[dict[str, Any]]) -> str:
    local_prefix = ",".join(a.sha256[:8] for a in local_assets[:3])
    if len(local_assets) > 3:
        local_prefix += ",..."
    remote_hashes: list[str] = []
    for item in remote_images[:3]:
        h = str(item.get("sha256") or item.get("sha1") or "none").strip().lower()
        remote_hashes.append(h[:8])
    remote_prefix = ",".join(remote_hashes) if remote_hashes else "none"
    if len(remote_images) > 3:
        remote_prefix += ",..."
    return f"local=`{local_prefix}` vs play=`{remote_prefix}`"


def format_asset_report_markdown(
    *,
    heading: str,
    package_name: str,
    language: str,
    listing_spec: StoreListingSpec | None,
    remote_listing: dict[str, Any] | None,
    listing_diffs: list[str],
    listing_updated: bool,
    image_rows: list[dict[str, Any]],
    committed: bool,
    dry_run: bool,
) -> str:
    lines = [
        f"### {heading} (`{package_name}`, `{language}`)",
        "",
    ]
    if listing_spec is not None:
        if listing_diffs:
            listing_status = (
                "UPDATED" if listing_updated else ("WOULD UPDATE" if dry_run else "OUT OF SYNC")
            )
            diff_detail = ", ".join(f"`{f}`" for f in listing_diffs)
        else:
            listing_status = "IN SYNC"
            diff_detail = "none"
        remote = remote_listing or {}
        remote_title = normalize_listing_text(str(remote.get("title") or ""))
        remote_short = normalize_listing_text(str(remote.get("shortDescription") or ""))
        remote_full_len = len(normalize_listing_text(str(remote.get("fullDescription") or "")))
        lines.extend(
            [
                f"- **Store Listing Copy (`STORE_LISTING.md`):** `{listing_status}` "
                f"(changed fields: {diff_detail})",
                f"  - Title: local `{listing_spec.title}` ({len(listing_spec.title)}/{MAX_TITLE_CHARS}) "
                f"| play `{remote_title or '(empty)'}`",
                f"  - Short description: local `{len(listing_spec.short_description)}/{MAX_SHORT_DESCRIPTION_CHARS}` chars "
                f"| play `{len(remote_short)}` chars"
                + (f" (`{remote_short}`)" if remote_short and "shortDescription" in listing_diffs else ""),
                f"  - Full description: local `{len(listing_spec.full_description)}/{MAX_FULL_DESCRIPTION_CHARS}` chars "
                f"| play `{remote_full_len}` chars",
                "",
            ]
        )

    if image_rows:
        lines.extend(
            [
                "| Play Image Type | Label | Local Files | Play Files | Status | Hash Check |",
                "| :--- | :--- | :---: | :---: | :---: | :--- |",
            ]
        )
        for row in image_rows:
            lines.append(
                f"| `{row['image_type']}` | {row['label']} | {row['local_count']} | "
                f"{row['remote_count']} | `{row['status']}` | {row.get('hash_detail', '—')} |"
            )
        lines.append("")

    if dry_run:
        lines.append("_Read-only inspection (`diff-assets` / `--dry-run`): no changes were committed to Google Play._")
    elif committed:
        lines.append("_All detected Store Listing and graphic asset updates were committed atomically to Google Play._")
    else:
        lines.append("_Google Play already matches `play_store_assets/`; no Play edit was committed._")

    return "\n".join(lines) + "\n"


def run_asset_sync(
    client: PlayPublisherClient,
    *,
    assets_dir: Path,
    listing_path: Path,
    language: str,
    sync_listing: bool,
    sync_images: bool,
    selected_image_types: set[str] | None,
    changes_not_sent_for_review: bool,
    dry_run: bool,
) -> dict[str, Any]:
    """Inspect or idempotently sync listing text and PNG graphic assets to Google Play."""
    if not sync_listing and not sync_images:
        raise ValueError("At least one of sync_listing or sync_images must be enabled.")

    listing_spec = parse_store_listing_markdown(listing_path, language=language) if sync_listing else None
    local_images = (
        discover_local_image_assets(assets_dir, selected_types=selected_image_types)
        if sync_images
        else {}
    )
    spec_by_type = {spec.image_type: spec for spec in IMAGE_TYPE_SPECS}

    edit_id = client.insert_edit()
    committed = False
    try:
        remote_listing = (
            _fetch_remote_listing_or_none(client, edit_id, language) if listing_spec is not None else None
        )
        listing_diffs = (
            listing_changed_fields(listing_spec, remote_listing) if listing_spec is not None else []
        )

        remote_images_by_type: dict[str, list[dict[str, Any]]] = {}
        changed_image_types: list[str] = []
        image_rows: list[dict[str, Any]] = []

        for image_type, local_list in local_images.items():
            remote_list = _fetch_remote_images_or_empty(client, edit_id, language, image_type)
            remote_images_by_type[image_type] = remote_list
            in_sync = images_match_remote(local_list, remote_list)
            if not in_sync:
                changed_image_types.append(image_type)
            image_rows.append(
                {
                    "image_type": image_type,
                    "label": spec_by_type[image_type].label,
                    "local_count": len(local_list),
                    "remote_count": len(remote_list),
                    "in_sync": in_sync,
                    "status": "IN SYNC" if in_sync else ("WOULD UPDATE" if dry_run else "PENDING"),
                    "hash_detail": "match" if in_sync else _short_hash_summary(local_list, remote_list),
                }
            )

        has_changes = bool(listing_diffs or changed_image_types)
        listing_updated = False

        if has_changes and not dry_run:
            if listing_spec is not None and listing_diffs:
                payload: dict[str, Any] = {
                    "language": language,
                    "title": listing_spec.title,
                    "shortDescription": listing_spec.short_description,
                    "fullDescription": listing_spec.full_description,
                }
                existing_video = str((remote_listing or {}).get("video") or "").strip()
                if existing_video:
                    payload["video"] = existing_video
                client.update_listing(edit_id, language, payload)
                listing_updated = True

            for row in image_rows:
                if row["in_sync"]:
                    continue
                image_type = row["image_type"]
                spec = spec_by_type[image_type]
                if not spec.is_single and remote_images_by_type.get(image_type):
                    client.delete_all_images(edit_id, language, image_type)
                for asset in local_images[image_type]:
                    client.upload_image(edit_id, language, image_type, asset.path)
                row["status"] = "UPDATED"
                row["remote_count"] = len(local_images[image_type])
                row["hash_detail"] = "synced"

            client.commit_edit(
                edit_id,
                changes_not_sent_for_review=changes_not_sent_for_review,
            )
            committed = True
        else:
            for row in image_rows:
                if not row["in_sync"] and not dry_run:
                    row["status"] = "OUT OF SYNC"

        heading = (
            "Google Play Store Asset Diff"
            if dry_run
            else (
                "Google Play Store Assets Synced"
                if committed
                else "Google Play Store Assets Already In Sync"
            )
        )
        summary_md = format_asset_report_markdown(
            heading=heading,
            package_name=client.package_name,
            language=language,
            listing_spec=listing_spec,
            remote_listing=remote_listing,
            listing_diffs=listing_diffs,
            listing_updated=listing_updated,
            image_rows=image_rows,
            committed=committed,
            dry_run=dry_run,
        )
        print(summary_md)
        write_step_summary(summary_md)

        return {
            "in_sync": not has_changes,
            "synced": committed,
            "listing_changed_fields": listing_diffs,
            "changed_image_types": changed_image_types,
            "updated_listing": listing_updated,
            "updated_image_types": changed_image_types if committed else [],
        }
    finally:
        if not committed:
            client.delete_edit(edit_id)


def _parse_image_types_filter(raw: str) -> set[str] | None:
    cleaned = (raw or "").strip()
    if not cleaned:
        return None
    valid = {spec.image_type for spec in IMAGE_TYPE_SPECS}
    selected = {part.strip() for part in cleaned.split(",") if part.strip()}
    unknown = sorted(selected - valid)
    if unknown:
        raise ValueError(
            f"Unknown image type(s): {', '.join(unknown)}. Valid types: {', '.join(sorted(valid))}"
        )
    return selected


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Inspect or idempotently sync play_store_assets/ to Google Play Console."
    )
    parser.add_argument("--credentials", default="", help="Path to Google service account JSON key.")
    parser.add_argument("--package-name", default=DEFAULT_PACKAGE_NAME)
    parser.add_argument("--github-output", default="", help="Optional $GITHUB_OUTPUT file path.")

    subparsers = parser.add_subparsers(dest="command", required=True)
    for cmd, help_text in (
        ("validate-local", "Validate STORE_LISTING.md limits and local PNG assets offline (no API calls)."),
        ("diff-assets", "Read-only diff of play_store_assets/ against Google Play Console."),
        ("sync-assets", "Idempotently sync changed Store Listing text and PNG assets to Google Play."),
    ):
        sub = subparsers.add_parser(cmd, help=help_text)
        sub.add_argument("--assets-dir", default=DEFAULT_ASSETS_DIR)
        sub.add_argument("--listing-file", default=DEFAULT_LISTING_FILE)
        sub.add_argument("--language", default=DEFAULT_LANGUAGE)
        sub.add_argument("--no-sync-listing", action="store_true")
        sub.add_argument("--no-sync-images", action="store_true")
        sub.add_argument("--image-types", default="", help="Comma-separated subset of AppImageType values.")
        if cmd == "sync-assets":
            sub.add_argument("--changes-not-sent-for-review", action="store_true")
            sub.add_argument("--dry-run", action="store_true")

    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    output_path = Path(args.github_output) if (args.github_output or "").strip() else None

    try:
        assets_dir = Path(args.assets_dir)
        listing_path = Path(args.listing_file)
        language = (args.language or DEFAULT_LANGUAGE).strip() or DEFAULT_LANGUAGE
        sync_listing = not bool(args.no_sync_listing)
        sync_images = not bool(args.no_sync_images)
        selected_types = _parse_image_types_filter(args.image_types)

        if args.command == "validate-local":
            if sync_listing:
                spec = parse_store_listing_markdown(listing_path, language=language)
                print(
                    f"Listing OK ({language}): title={len(spec.title)}/{MAX_TITLE_CHARS}, "
                    f"short={len(spec.short_description)}/{MAX_SHORT_DESCRIPTION_CHARS}, "
                    f"full={len(spec.full_description)}/{MAX_FULL_DESCRIPTION_CHARS}"
                )
            if sync_images:
                found = discover_local_image_assets(assets_dir, selected_types=selected_types)
                total = sum(len(v) for v in found.values())
                print(f"Images OK: {total} PNG file(s) across {len(found)} Play Console image slot(s).")
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

        is_dry_run = args.command == "diff-assets" or bool(getattr(args, "dry_run", False))
        result = run_asset_sync(
            client,
            assets_dir=assets_dir,
            listing_path=listing_path,
            language=language,
            sync_listing=sync_listing,
            sync_images=sync_images,
            selected_image_types=selected_types,
            changes_not_sent_for_review=bool(getattr(args, "changes_not_sent_for_review", False)),
            dry_run=is_dry_run,
        )
        write_github_output(
            output_path,
            {
                "assets_in_sync": "true" if result["in_sync"] else "false",
                "assets_synced": "true" if result["synced"] else "false",
                "updated_listing": "true" if result["updated_listing"] else "false",
                "updated_image_types": ",".join(result["updated_image_types"]),
            },
        )
        return 0
    except (ValueError, PlayApiError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
