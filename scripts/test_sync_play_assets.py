#!/usr/bin/env python3
"""Unit tests for scripts/sync_play_assets.py (stdlib only, no live network)."""

from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import promote_play_release as ppr  # noqa: E402
import sync_play_assets as spa  # noqa: E402


class FakeResponse:
    def __init__(self, status_code: int, payload: Any = None, text: str | None = None) -> None:
        self.status_code = status_code
        self._payload = payload
        if text is not None:
            self.text = text
        elif payload is None:
            self.text = ""
        else:
            self.text = json.dumps(payload)

    def json(self) -> Any:
        return self._payload


class RecordingAssetSession:
    """In-memory mock of requests/AuthorizedSession for listings & images edits."""

    def __init__(self, responses: list[FakeResponse]) -> None:
        self._responses = list(responses)
        self.calls: list[dict[str, Any]] = []

    def request(
        self,
        method: str,
        url: str,
        *,
        params: dict[str, str] | None = None,
        json: dict[str, Any] | None = None,
        data: bytes | None = None,
        headers: dict[str, str] | None = None,
        timeout: int = 30,
    ) -> FakeResponse:
        self.calls.append(
            {
                "method": method,
                "url": url,
                "params": params,
                "json": json,
                "data_len": len(data) if data is not None else None,
                "headers": headers,
                "timeout": timeout,
            }
        )
        if not self._responses:
            raise AssertionError(f"Unexpected HTTP call: {method} {url}")
        return self._responses.pop(0)


def _write_fake_png(path: Path, body: bytes) -> tuple[str, str]:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = spa.PNG_SIGNATURE + body
    path.write_bytes(raw)
    return hashlib.sha256(raw).hexdigest(), hashlib.sha1(raw).hexdigest()


def _create_sample_assets_tree(root: Path) -> tuple[Path, Path, dict[str, list[tuple[str, str]]]]:
    assets_dir = root / "play_store_assets"
    listing_path = assets_dir / "STORE_LISTING.md"
    assets_dir.mkdir(parents=True, exist_ok=True)
    listing_path.write_text(
        "# Google Play Store Listing\n\n"
        "- **App Title (19 / 30 chars max):** `WeMi Switch Control`\n\n"
        "## Short Description (76 / 80 chars max)\n"
        "Local Wi-Fi controller for Belkin's discontinued smart switch platform & TV.\n\n"
        "## Full Description\n"
        "WeMi Switch Control brings **fast** local control.\n\n"
        "### Key Features\n"
        "- **One-Tap Toggle:** Control `LAN` switches locally.\n\n"
        "## Complete Google Play Console Asset Inventory\n"
        "1. Ignored section below Full Description.\n",
        encoding="utf-8",
    )

    hashes: dict[str, list[tuple[str, str]]] = {}
    hashes["icon"] = [_write_fake_png(assets_dir / "play_store_icon_512x512.png", b"icon-bytes")]
    hashes["featureGraphic"] = [
        _write_fake_png(assets_dir / "feature_graphic_1024x500.png", b"feature-bytes")
    ]
    hashes["tvBanner"] = [_write_fake_png(assets_dir / "tv/tv_banner_1280x720.png", b"tv-banner-1280")]
    # APK drawable banner should be ignored by Play Console asset discovery
    _write_fake_png(assets_dir / "tv/tv_banner_320x180.png", b"apk-leanback-banner")
    # Legacy top-level screenshots should be ignored
    _write_fake_png(assets_dir / "screenshots/screenshot_1_legacy.png", b"legacy-ignore")

    hashes["phoneScreenshots"] = [
        _write_fake_png(assets_dir / "screenshots/phone/phone_1.png", b"phone-1"),
        _write_fake_png(assets_dir / "screenshots/phone/phone_2.png", b"phone-2"),
    ]
    hashes["sevenInchScreenshots"] = [
        _write_fake_png(assets_dir / "screenshots/tablet_7_inch/t7_1.png", b"t7-1"),
    ]
    hashes["tenInchScreenshots"] = [
        _write_fake_png(assets_dir / "screenshots/tablet_10_inch/t10_1.png", b"t10-1"),
    ]
    hashes["tvScreenshots"] = [
        _write_fake_png(assets_dir / "screenshots/android_tv/tv_1.png", b"tv-1"),
    ]
    return assets_dir, listing_path, hashes


class SyncPlayAssetsTests(unittest.TestCase):
    def test_real_repo_play_store_assets_validate_cleanly(self) -> None:
        listing = spa.parse_store_listing_markdown(REPO_ROOT / "play_store_assets/STORE_LISTING.md")
        self.assertEqual(listing.title, "WeMi Switch Control")
        self.assertLessEqual(len(listing.title), spa.MAX_TITLE_CHARS)
        self.assertLessEqual(len(listing.short_description), spa.MAX_SHORT_DESCRIPTION_CHARS)
        self.assertLessEqual(len(listing.full_description), spa.MAX_FULL_DESCRIPTION_CHARS)
        self.assertNotIn("**", listing.full_description)
        self.assertNotIn("`", listing.full_description)

        images = spa.discover_local_image_assets(REPO_ROOT / "play_store_assets")
        self.assertEqual(len(images["icon"]), 1)
        self.assertEqual(len(images["featureGraphic"]), 1)
        self.assertEqual(len(images["tvBanner"]), 1)
        self.assertEqual(images["tvBanner"][0].path.name, "tv_banner_1280x720.png")
        self.assertEqual(len(images["phoneScreenshots"]), 4)
        self.assertEqual(len(images["sevenInchScreenshots"]), 2)
        self.assertEqual(len(images["tenInchScreenshots"]), 2)
        self.assertEqual(len(images["tvScreenshots"]), 3)
        self.assertEqual(sum(len(v) for v in images.values()), 14)

    def test_parse_store_listing_rejects_overflow_and_trademark(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            too_long = Path(tmp) / "LONG.md"
            too_long.write_text(
                "- **App Title:** `WeMi Switch Control`\n"
                "## Short Description\nShort ok.\n"
                "## Full Description\n" + ("x" * 4001) + "\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "4000"):
                spa.parse_store_listing_markdown(too_long)

            trademark = Path(tmp) / "TM.md"
            trademark.write_text(
                "- **App Title:** `WeMi Switch Control`\n"
                "## Short Description\nWeMo controller\n"
                "## Full Description\nBody\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "Trademark"):
                spa.parse_store_listing_markdown(trademark)

    def test_sync_assets_is_noop_when_play_matches_local_copy_and_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            assets_dir, listing_path, hashes = _create_sample_assets_tree(Path(tmp))
            parsed = spa.parse_store_listing_markdown(listing_path)

            responses = [
                FakeResponse(200, {"id": "edit-noop"}),
                FakeResponse(
                    200,
                    {
                        "language": "en-US",
                        "title": parsed.title,
                        "shortDescription": parsed.short_description,
                        "fullDescription": parsed.full_description,
                    },
                ),
            ]
            for spec in spa.IMAGE_TYPE_SPECS:
                responses.append(
                    FakeResponse(
                        200,
                        {
                            "images": [
                                {"id": f"{spec.image_type}-{i}", "sha256": sha256, "sha1": sha1}
                                for i, (sha256, sha1) in enumerate(hashes[spec.image_type])
                            ]
                        },
                    )
                )
            responses.append(FakeResponse(204))  # delete_edit

            session = RecordingAssetSession(responses)
            client = ppr.PlayPublisherClient("com.example.app", session=session)
            result = spa.run_asset_sync(
                client,
                assets_dir=assets_dir,
                listing_path=listing_path,
                language="en-US",
                sync_listing=True,
                sync_images=True,
                selected_image_types=None,
                changes_not_sent_for_review=False,
                dry_run=False,
            )

            self.assertTrue(result["in_sync"])
            self.assertFalse(result["synced"])
            self.assertEqual(result["updated_image_types"], [])
            # Must NOT PUT listing, DELETE images, upload, or commit
            methods = [c["method"] for c in session.calls]
            self.assertEqual(methods.count("PUT"), 0)
            self.assertEqual(methods.count("POST"), 1)  # only insert_edit
            self.assertEqual(session.calls[-1]["method"], "DELETE")
            self.assertTrue(session.calls[-1]["url"].endswith("/edits/edit-noop"))

    def test_sync_assets_updates_only_changed_listing_and_changed_image_types(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            assets_dir, listing_path, hashes = _create_sample_assets_tree(Path(tmp))
            parsed = spa.parse_store_listing_markdown(listing_path)

            responses = [
                FakeResponse(200, {"id": "edit-sync"}),
                # Remote listing has stale fullDescription + existing promo video URL
                FakeResponse(
                    200,
                    {
                        "language": "en-US",
                        "title": parsed.title,
                        "shortDescription": parsed.short_description,
                        "fullDescription": "Old description",
                        "video": "https://youtu.be/demo123",
                    },
                ),
            ]
            # icon differs (single-image slot -> upload directly without deleteall)
            responses.append(FakeResponse(200, {"images": [{"id": "old-icon", "sha256": "0000"}]}))
            # featureGraphic & tvBanner match
            for slot in ("featureGraphic", "tvBanner"):
                responses.append(
                    FakeResponse(
                        200,
                        {"images": [{"id": slot, "sha256": hashes[slot][0][0]}]},
                    )
                )
            # phoneScreenshots differs (multi-image slot -> deleteall + upload 2 files)
            responses.append(FakeResponse(200, {"images": [{"id": "old-p1", "sha1": "deadbeef"}]}))
            # remaining screenshot slots match
            for slot in ("sevenInchScreenshots", "tenInchScreenshots", "tvScreenshots"):
                responses.append(
                    FakeResponse(
                        200,
                        {"images": [{"id": slot, "sha1": hashes[slot][0][1]}]},
                    )
                )
            # Write calls: PUT listing, POST icon upload, DELETE phoneScreenshots, POST p1, POST p2, POST commit
            responses.extend(
                [
                    FakeResponse(200, {"language": "en-US"}),  # update_listing
                    FakeResponse(200, {"image": {"id": "new-icon"}}),  # upload icon
                    FakeResponse(200, {"deleted": [{"id": "old-p1"}]}),  # deleteall phoneScreenshots
                    FakeResponse(200, {"image": {"id": "new-p1"}}),  # upload phone_1
                    FakeResponse(200, {"image": {"id": "new-p2"}}),  # upload phone_2
                    FakeResponse(200, {"id": "edit-sync"}),  # commit_edit
                ]
            )

            session = RecordingAssetSession(responses)
            client = ppr.PlayPublisherClient("com.example.app", session=session)
            result = spa.run_asset_sync(
                client,
                assets_dir=assets_dir,
                listing_path=listing_path,
                language="en-US",
                sync_listing=True,
                sync_images=True,
                selected_image_types=None,
                changes_not_sent_for_review=True,
                dry_run=False,
            )

            self.assertFalse(result["in_sync"])
            self.assertTrue(result["synced"])
            self.assertTrue(result["updated_listing"])
            self.assertEqual(result["listing_changed_fields"], ["fullDescription"])
            self.assertEqual(result["updated_image_types"], ["icon", "phoneScreenshots"])

            # Verify existing YouTube video URL was preserved in PUT listing
            put_call = [c for c in session.calls if c["method"] == "PUT"][0]
            self.assertEqual(put_call["json"]["video"], "https://youtu.be/demo123")
            self.assertEqual(put_call["json"]["fullDescription"], parsed.full_description)

            # Verify icon used upload endpoint directly (no DELETE /icon)
            delete_calls = [c for c in session.calls if c["method"] == "DELETE"]
            self.assertEqual(len(delete_calls), 1)
            self.assertTrue(delete_calls[0]["url"].endswith("/listings/en-US/phoneScreenshots"))

            upload_calls = [c for c in session.calls if "/upload/androidpublisher/v3/" in c["url"]]
            self.assertEqual(len(upload_calls), 3)  # icon + 2 phone screenshots
            for call in upload_calls:
                self.assertEqual(call["params"], {"uploadType": "media"})
                self.assertEqual(call["headers"], {"Content-Type": "image/png"})
                self.assertGreater(call["data_len"], len(spa.PNG_SIGNATURE))

            commit_call = session.calls[-1]
            self.assertTrue(commit_call["url"].endswith("/edits/edit-sync:commit"))
            self.assertEqual(commit_call["params"], {"changesNotSentForReview": "true"})

    def test_diff_assets_dry_run_never_mutates_and_deletes_edit(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            assets_dir, listing_path, _ = _create_sample_assets_tree(Path(tmp))
            responses = [
                FakeResponse(200, {"id": "edit-diff"}),
                FakeResponse(404, {"error": {"message": "Listing not found"}}),
            ]
            for _ in spa.IMAGE_TYPE_SPECS:
                responses.append(FakeResponse(200, {"images": []}))
            responses.append(FakeResponse(204))  # delete_edit

            session = RecordingAssetSession(responses)
            client = ppr.PlayPublisherClient("com.example.app", session=session)
            result = spa.run_asset_sync(
                client,
                assets_dir=assets_dir,
                listing_path=listing_path,
                language="en-US",
                sync_listing=True,
                sync_images=True,
                selected_image_types=None,
                changes_not_sent_for_review=False,
                dry_run=True,
            )

            self.assertFalse(result["in_sync"])
            self.assertFalse(result["synced"])
            self.assertEqual(result["listing_changed_fields"], ["title", "shortDescription", "fullDescription"])
            self.assertEqual(len(result["changed_image_types"]), len(spa.IMAGE_TYPE_SPECS))
            self.assertEqual(session.calls[-1]["method"], "DELETE")
            self.assertTrue(session.calls[-1]["url"].endswith("/edits/edit-diff"))

    def test_sync_assets_deletes_edit_when_upload_returns_403(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            assets_dir, listing_path, _ = _create_sample_assets_tree(Path(tmp))
            responses = [
                FakeResponse(200, {"id": "edit-403"}),
                FakeResponse(200, {"images": []}),  # list icon
                FakeResponse(
                    403,
                    {"error": {"message": "The caller does not have permission"}},
                ),
                FakeResponse(204),  # cleanup delete_edit
            ]
            session = RecordingAssetSession(responses)
            client = ppr.PlayPublisherClient("com.example.app", session=session)

            with self.assertRaises(ppr.PlayApiError) as ctx:
                spa.run_asset_sync(
                    client,
                    assets_dir=assets_dir,
                    listing_path=listing_path,
                    language="en-US",
                    sync_listing=False,
                    sync_images=True,
                    selected_image_types={"icon"},
                    changes_not_sent_for_review=False,
                    dry_run=False,
                )

            self.assertIn("Manage store presence", str(ctx.exception))
            self.assertEqual(session.calls[-1]["method"], "DELETE")
            self.assertTrue(session.calls[-1]["url"].endswith("/edits/edit-403"))

    def test_cli_validate_local_requires_no_credentials(self) -> None:
        exit_code = spa.main(
            [
                "validate-local",
                "--assets-dir",
                str(REPO_ROOT / "play_store_assets"),
                "--listing-file",
                str(REPO_ROOT / "play_store_assets/STORE_LISTING.md"),
            ]
        )
        self.assertEqual(exit_code, 0)


if __name__ == "__main__":
    unittest.main()
