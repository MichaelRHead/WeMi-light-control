#!/usr/bin/env python3
"""Unit tests for scripts/upload_play_bundle.py using synthetic AABs and mocked HTTP responses."""

from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import promote_play_release as promo  # noqa: E402
import upload_play_bundle as mod  # noqa: E402


class FakeResponse:
    def __init__(self, status_code: int, payload: Any = None, text: str | None = None) -> None:
        self.status_code = status_code
        if text is not None:
            self.text = text
        elif payload is None:
            self.text = ""
        else:
            self.text = json.dumps(payload)

    def json(self) -> Any:
        return json.loads(self.text)


class FakeUploadSession:
    def __init__(self, routes: list[tuple[str, str, FakeResponse]]) -> None:
        self.routes = list(routes)
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
                "data": data,
                "headers": headers,
                "timeout": timeout,
            }
        )
        if not self.routes:
            raise AssertionError(f"Unexpected request: {method} {url}")
        expected_method, expected_suffix, response = self.routes.pop(0)
        assert method == expected_method, f"Expected {expected_method}, got {method}"
        assert url.endswith(expected_suffix), f"Expected URL ending with {expected_suffix}, got {url}"
        return response


def create_synthetic_aab(
    path: Path,
    *,
    signed: bool = True,
    include_manifest: bool = True,
    include_proguard_map: bool = True,
    extra_entries: dict[str, bytes] | None = None,
) -> Path:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        if include_manifest:
            zf.writestr(mod.REQUIRED_AAB_MANIFEST_ENTRY, b"<manifest/>")
        if signed:
            zf.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n")
            zf.writestr("META-INF/UPLOAD.SF", b"Signature-Version: 1.0\n")
            zf.writestr("META-INF/UPLOAD.RSA", b"\x30\x82\x00\x01")
        if include_proguard_map:
            zf.writestr(mod.PROGUARD_MAP_ENTRY, b"# R8 mapping\n")
        for name, content in (extra_entries or {}).items():
            zf.writestr(name, content)
    return path


class UploadPlayBundleTests(unittest.TestCase):
    def test_inspect_bundle_archive_accepts_signed_pure_kotlin_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            aab = create_synthetic_aab(Path(tmpdir) / "app-release.aab")
            info = mod.inspect_bundle_archive(aab)
            self.assertEqual(info.signature_files, ("META-INF/UPLOAD.RSA",))
            self.assertTrue(info.has_proguard_map)
            self.assertEqual(info.forbidden_binaries, ())
            self.assertEqual(len(info.sha256), 64)

    def test_inspect_bundle_archive_rejects_unsigned_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            aab = create_synthetic_aab(Path(tmpdir) / "unsigned.aab", signed=False)
            with self.assertRaisesRegex(ValueError, "appears unsigned"):
                mod.inspect_bundle_archive(aab)

    def test_inspect_bundle_archive_rejects_missing_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            aab = create_synthetic_aab(Path(tmpdir) / "bad.aab", include_manifest=False)
            with self.assertRaisesRegex(ValueError, "missing required entry"):
                mod.inspect_bundle_archive(aab)

    def test_inspect_bundle_archive_rejects_disallowed_native_or_font_binaries(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            aab = create_synthetic_aab(
                Path(tmpdir) / "native.aab",
                extra_entries={"base/lib/arm64-v8a/libfoo.so": b"\x7fELF"},
            )
            with self.assertRaisesRegex(ValueError, "disallowed native/font binaries"):
                mod.inspect_bundle_archive(aab)

            # Escape hatch allows override when explicitly requested
            info = mod.inspect_bundle_archive(aab, allow_native_or_fonts=True)
            self.assertEqual(info.forbidden_binaries, ("base/lib/arm64-v8a/libfoo.so",))

    def test_dry_run_cli_validates_without_credentials(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            tmp = Path(tmpdir)
            aab = create_synthetic_aab(tmp / "app-release.aab")
            notes = tmp / "RELEASE_NOTES.txt"
            notes.write_text("Bug fixes and improvements.", encoding="utf-8")
            gradle = tmp / "build.gradle.kts"
            gradle.write_text('versionCode = 39\nversionName = "39.0"\n', encoding="utf-8")
            out_file = tmp / "github_output.txt"

            rc = mod.main(
                [
                    "--bundle",
                    str(aab),
                    "--gradle-file",
                    str(gradle),
                    "--release-notes-file",
                    str(notes),
                    "--dry-run",
                    "--github-output",
                    str(out_file),
                ]
            )
            self.assertEqual(rc, 0)
            output_text = out_file.read_text(encoding="utf-8")
            self.assertIn("uploaded=false", output_text)
            self.assertIn("version_code=39", output_text)
            self.assertIn("release_name=39.0", output_text)
            self.assertIn("has_proguard_map=true", output_text)

    def test_run_upload_bundle_uploads_assigns_track_and_commits(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            tmp = Path(tmpdir)
            aab = create_synthetic_aab(tmp / "app-release.aab")
            notes = tmp / "RELEASE_NOTES.txt"
            notes.write_text("v39.0 automated release notes.", encoding="utf-8")

            session = FakeUploadSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "edit-42"})),
                    (
                        "POST",
                        "/edits/edit-42/bundles",
                        FakeResponse(200, {"versionCode": 39, "sha256": "abc123"}),
                    ),
                    ("PUT", "/edits/edit-42/tracks/internal", FakeResponse(200, {"track": "internal"})),
                    ("POST", "/edits/edit-42:commit", FakeResponse(200, {"id": "edit-42"})),
                ]
            )
            client = promo.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)

            result = mod.run_upload_bundle(
                client,
                bundle_path=aab,
                track="internal",
                status="completed",
                expected_version_code=39,
                release_name="39.0",
                notes_source="file",
                notes_file=notes,
                notes_language="en-US",
            )

            self.assertTrue(result["uploaded"])
            self.assertEqual(result["version_code"], "39")
            self.assertEqual(result["track"], "internal")
            self.assertEqual(result["release_name"], "39.0")
            self.assertEqual(result["sha256"], "abc123")

            upload_call = session.calls[1]
            self.assertIn("/upload/androidpublisher/v3/", upload_call["url"])
            self.assertEqual(upload_call["params"], {"uploadType": "media"})
            self.assertEqual(upload_call["headers"], {"Content-Type": "application/octet-stream"})
            self.assertEqual(upload_call["data"], aab.read_bytes())

            track_call = session.calls[2]
            self.assertEqual(
                track_call["json"],
                {
                    "track": "internal",
                    "releases": [
                        {
                            "versionCodes": ["39"],
                            "status": "completed",
                            "name": "39.0",
                            "releaseNotes": [
                                {"language": "en-US", "text": "v39.0 automated release notes."}
                            ],
                        }
                    ],
                },
            )

    def test_run_upload_bundle_aborts_and_deletes_edit_on_version_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            tmp = Path(tmpdir)
            aab = create_synthetic_aab(tmp / "app-release.aab")
            session = FakeUploadSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "edit-99"})),
                    ("POST", "/edits/edit-99/bundles", FakeResponse(200, {"versionCode": 40})),
                    ("DELETE", "/edits/edit-99", FakeResponse(204)),
                ]
            )
            client = promo.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)

            with self.assertRaisesRegex(ValueError, "does not match expected versionCode"):
                mod.run_upload_bundle(
                    client,
                    bundle_path=aab,
                    track="internal",
                    expected_version_code=39,
                    notes_source="none",
                )
            self.assertEqual(len(session.routes), 0)

    def test_run_upload_bundle_deletes_edit_on_wrong_key_403_with_helpful_hint(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            tmp = Path(tmpdir)
            aab = create_synthetic_aab(tmp / "app-release.aab")
            session = FakeUploadSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "edit-403"})),
                    (
                        "POST",
                        "/edits/edit-403/bundles",
                        FakeResponse(
                            403,
                            {
                                "error": {
                                    "message": "The Android App Bundle was signed with the wrong key."
                                }
                            },
                        ),
                    ),
                    ("DELETE", "/edits/edit-403", FakeResponse(204)),
                ]
            )
            client = promo.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)

            with self.assertRaisesRegex(promo.PlayApiError, "upload key reset"):
                mod.run_upload_bundle(
                    client,
                    bundle_path=aab,
                    track="internal",
                    notes_source="none",
                )
            self.assertEqual(len(session.routes), 0)

    def test_validate_upload_options_checks_user_fraction(self) -> None:
        with self.assertRaisesRegex(ValueError, "strictly between 0 and 1"):
            mod.validate_upload_options(
                track="alpha", status="inProgress", user_fraction=None, notes_source="none"
            )
        with self.assertRaisesRegex(ValueError, "only valid when --status=inProgress"):
            mod.validate_upload_options(
                track="alpha", status="completed", user_fraction=0.25, notes_source="none"
            )

    def test_cli_returns_1_on_missing_credentials_without_dry_run(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            aab = create_synthetic_aab(Path(tmpdir) / "app-release.aab")
            stderr = io.StringIO()
            with redirect_stderr(stderr):
                rc = mod.main(["--bundle", str(aab), "--release-notes-source", "none"])
            self.assertEqual(rc, 1)
            self.assertIn("Missing --credentials", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
