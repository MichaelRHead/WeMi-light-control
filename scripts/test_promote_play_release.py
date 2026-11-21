#!/usr/bin/env python3
"""Unit tests for scripts/promote_play_release.py using mocked HTTP responses."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import Any

import promote_play_release as mod


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


class FakeSession:
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
        timeout: int = 30,
    ) -> FakeResponse:
        self.calls.append({"method": method, "url": url, "params": params, "json": json})
        if not self.routes:
            raise AssertionError(f"Unexpected request: {method} {url}")
        expected_method, expected_suffix, response = self.routes.pop(0)
        assert method == expected_method, f"Expected {expected_method}, got {method}"
        assert url.endswith(expected_suffix), f"Expected URL ending with {expected_suffix}, got {url}"
        return response


class PromotePlayReleaseTests(unittest.TestCase):
    def test_validate_service_account_rejects_api_key(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            key_file = Path(tmpdir) / "key.json"
            key_file.write_text("AIzaSyFakeApiKeyValue", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "not valid JSON"):
                mod.validate_service_account_file(key_file)

    def test_validate_service_account_accepts_valid_key(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            key_file = Path(tmpdir) / "sa.json"
            synthetic_pem = (
                "-----BEGIN " + "PRIVATE KEY-----\n"
                "SYNTHETIC_UNIT_TEST_VALUE\n"
                "-----END " + "PRIVATE KEY-----\n"
            )
            key_file.write_text(
                json.dumps(
                    {
                        "type": "service_account",
                        "client_email": "bot@example.iam.gserviceaccount.com",
                        "private_key": synthetic_pem,
                    }
                ),
                encoding="utf-8",
            )
            email = mod.validate_service_account_file(key_file)
            self.assertEqual(email, "bot@example.iam.gserviceaccount.com")

    def test_list_tracks_opens_reads_and_deletes_edit(self) -> None:
        session = FakeSession(
            [
                ("POST", "/edits", FakeResponse(200, {"id": "edit-1"})),
                (
                    "GET",
                    "/edits/edit-1/tracks",
                    FakeResponse(
                        200,
                        {
                            "tracks": [
                                {
                                    "track": "internal",
                                    "releases": [
                                        {
                                            "name": "28.0",
                                            "status": "completed",
                                            "versionCodes": ["28"],
                                        }
                                    ],
                                },
                                {
                                    "track": "alpha",
                                    "releases": [
                                        {
                                            "name": "27.0",
                                            "status": "completed",
                                            "versionCodes": ["27"],
                                        }
                                    ],
                                },
                            ]
                        },
                    ),
                ),
                ("DELETE", "/edits/edit-1", FakeResponse(204)),
            ]
        )
        client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
        tracks = mod.run_list_tracks(client)
        self.assertEqual([t["track"] for t in tracks], ["alpha", "internal"])
        self.assertEqual(session.calls[-1]["method"], "DELETE")

    def test_promote_internal_to_closed_track_with_file_notes(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            notes_path = Path(tmpdir) / "RELEASE_NOTES.txt"
            notes_path.write_text("WeMi Switch Control v28.0 updates.", encoding="utf-8")

            session = FakeSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "edit-2"})),
                    (
                        "GET",
                        "/edits/edit-2/tracks",
                        FakeResponse(
                            200,
                            {"tracks": [{"track": "internal"}, {"track": "alpha"}]},
                        ),
                    ),
                    (
                        "GET",
                        "/edits/edit-2/tracks/internal",
                        FakeResponse(
                            200,
                            {
                                "track": "internal",
                                "releases": [
                                    {
                                        "name": "28 (28.0)",
                                        "status": "completed",
                                        "versionCodes": ["28"],
                                    }
                                ],
                            },
                        ),
                    ),
                    (
                        "GET",
                        "/edits/edit-2/tracks/alpha",
                        FakeResponse(
                            200,
                            {
                                "track": "alpha",
                                "releases": [
                                    {
                                        "name": "27 (27.0)",
                                        "status": "completed",
                                        "versionCodes": ["27"],
                                    }
                                ],
                            },
                        ),
                    ),
                    ("PUT", "/edits/edit-2/tracks/alpha", FakeResponse(200, {"track": "alpha"})),
                    ("POST", "/edits/edit-2:commit", FakeResponse(200, {"id": "edit-2"})),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            promoted = mod.run_promote(
                client,
                from_track="internal",
                to_track="alpha",
                version_code=None,
                status="completed",
                user_fraction=None,
                notes_source="source_track",
                notes_file=notes_path,
                notes_language="en-US",
                release_name_override="",
                changes_not_sent_for_review=False,
                allow_same_version=False,
            )

            self.assertEqual(promoted["versionCodes"], ["28"])
            self.assertEqual(promoted["status"], "completed")
            self.assertEqual(
                promoted["releaseNotes"],
                [{"language": "en-US", "text": "WeMi Switch Control v28.0 updates."}],
            )
            # Committed edit must not be deleted afterward
            self.assertEqual(session.calls[-1]["method"], "POST")
            self.assertTrue(session.calls[-1]["url"].endswith(":commit"))

    def test_promote_rejects_duplicate_version_and_deletes_uncommitted_edit(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            notes_path = Path(tmpdir) / "RELEASE_NOTES.txt"
            session = FakeSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "edit-3"})),
                    ("GET", "/edits/edit-3/tracks", FakeResponse(200, {"tracks": []})),
                    (
                        "GET",
                        "/edits/edit-3/tracks/internal",
                        FakeResponse(
                            200,
                            {
                                "track": "internal",
                                "releases": [{"status": "completed", "versionCodes": ["28"]}],
                            },
                        ),
                    ),
                    (
                        "GET",
                        "/edits/edit-3/tracks/alpha",
                        FakeResponse(
                            200,
                            {
                                "track": "alpha",
                                "releases": [{"status": "completed", "versionCodes": ["28"]}],
                            },
                        ),
                    ),
                    ("DELETE", "/edits/edit-3", FakeResponse(204)),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            with self.assertRaisesRegex(ValueError, "already has versionCodes"):
                mod.run_promote(
                    client,
                    from_track="internal",
                    to_track="alpha",
                    version_code=None,
                    status="completed",
                    user_fraction=None,
                    notes_source="none",
                    notes_file=notes_path,
                    notes_language="en-US",
                    release_name_override="",
                    changes_not_sent_for_review=False,
                    allow_same_version=False,
                )
            self.assertEqual(session.calls[-1]["method"], "DELETE")

    def test_format_api_error_includes_permission_hint(self) -> None:
        msg = mod._format_api_error(
            403,
            json.dumps({"error": {"message": "The caller does not have permission"}}),
        )
        self.assertIn("Users and permissions", msg)
        self.assertIn("24 hours", msg)

    def test_parse_gradle_version_code_and_read_release_state(self) -> None:
        gradle = 'android {\n  defaultConfig {\n    versionCode = 29\n    versionName = "29.0"\n  }\n}\n'
        self.assertEqual(mod.parse_gradle_version_code(gradle), 29)
        self.assertIsNone(mod.parse_gradle_version_code("android {}"))

        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = Path(tmpdir) / "RELEASE_SIGN_OFF.md"
            sign_off.write_text(
                "Header\n<!-- RELEASE_STATE_BEGIN -->\n"
                "RELEASE_VERSION: v28.0\n"
                "RELEASE_STATUS: APPROVED_FOR_PLAY_STORE\n"
                "CURRENT_TURN: NONE\n"
                "<!-- RELEASE_STATE_END -->\n",
                encoding="utf-8",
            )
            state = mod.read_release_sign_off_state(sign_off)
            self.assertEqual(state["RELEASE_VERSION"], "v28.0")
            self.assertEqual(state["RELEASE_STATUS"], "APPROVED_FOR_PLAY_STORE")
            self.assertEqual(state["CURRENT_TURN"], "NONE")

    def _write_sign_off(self, tmpdir: str, status: str) -> Path:
        path = Path(tmpdir) / "RELEASE_SIGN_OFF.md"
        path.write_text(
            f"<!-- RELEASE_STATE_BEGIN -->\nRELEASE_STATUS: {status}\nCURRENT_TURN: NONE\n<!-- RELEASE_STATE_END -->\n",
            encoding="utf-8",
        )
        return path

    def test_auto_promote_skips_when_version_code_did_not_increase(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "APPROVED_FOR_PLAY_STORE")
            session = FakeSession([])
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            result = mod.run_auto_promote(
                client,
                from_track="internal",
                to_track="alpha",
                current_version_code=29,
                previous_version_code=29,
                sign_off_path=sign_off,
                require_approved=True,
                poll_attempts=3,
                poll_interval_sec=0.0,
                status="completed",
                notes_source="none",
                notes_file=Path(tmpdir) / "RELEASE_NOTES.txt",
                notes_language="en-US",
                changes_not_sent_for_review=False,
            )
            self.assertFalse(result["promoted"])
            self.assertIn("did not increase", result["skip_reason"])
            self.assertEqual(session.calls, [])

    def test_auto_promote_skips_when_release_status_not_approved(self) -> None:
        for status in ("IN_REVIEW", "PROMOTED_TO_CLOSED_TESTING", "AWAITING_FINAL_CONCLUSION"):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as tmpdir:
                sign_off = self._write_sign_off(tmpdir, status)
                session = FakeSession([])
                client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
                result = mod.run_auto_promote(
                    client,
                    from_track="internal",
                    to_track="alpha",
                    current_version_code=30,
                    previous_version_code=29,
                    sign_off_path=sign_off,
                    require_approved=True,
                    poll_attempts=2,
                    poll_interval_sec=0.0,
                    status="completed",
                    notes_source="none",
                    notes_file=Path(tmpdir) / "RELEASE_NOTES.txt",
                    notes_language="en-US",
                    changes_not_sent_for_review=False,
                )
                self.assertFalse(result["promoted"])
                self.assertIn(status, result["skip_reason"])
                self.assertEqual(session.calls, [])

    def test_auto_promote_promotes_matching_internal_release_after_poll(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "APPROVED_FOR_PLAY_STORE")
            notes_path = Path(tmpdir) / "RELEASE_NOTES.txt"
            notes_path.write_text("WeMi v29.0 Closed testing release.", encoding="utf-8")
            sleeps: list[float] = []

            session = FakeSession(
                [
                    # Poll attempt 1: Play internal still on 28
                    ("POST", "/edits", FakeResponse(200, {"id": "poll-1"})),
                    (
                        "GET",
                        "/edits/poll-1/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"status": "completed", "versionCodes": ["28"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/poll-1/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"status": "completed", "versionCodes": ["28"]}]}),
                    ),
                    ("DELETE", "/edits/poll-1", FakeResponse(204)),
                    # Poll attempt 2: Play internal now has 29
                    ("POST", "/edits", FakeResponse(200, {"id": "poll-2"})),
                    (
                        "GET",
                        "/edits/poll-2/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "0.0.0.29", "status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/poll-2/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "0.0.0.28", "status": "completed", "versionCodes": ["28"]}]}),
                    ),
                    ("DELETE", "/edits/poll-2", FakeResponse(204)),
                    # Promotion edit
                    ("POST", "/edits", FakeResponse(200, {"id": "promo-1"})),
                    ("GET", "/edits/promo-1/tracks", FakeResponse(200, {"tracks": [{"track": "internal"}, {"track": "alpha"}]})),
                    (
                        "GET",
                        "/edits/promo-1/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "0.0.0.29", "status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/promo-1/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "0.0.0.28", "status": "completed", "versionCodes": ["28"]}]}),
                    ),
                    ("PUT", "/edits/promo-1/tracks/alpha", FakeResponse(200, {"track": "alpha"})),
                    ("POST", "/edits/promo-1:commit", FakeResponse(200, {"id": "promo-1"})),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            result = mod.run_auto_promote(
                client,
                from_track="internal",
                to_track="alpha",
                current_version_code=29,
                previous_version_code=28,
                sign_off_path=sign_off,
                require_approved=True,
                poll_attempts=3,
                poll_interval_sec=15.0,
                status="completed",
                notes_source="source_track",
                notes_file=notes_path,
                notes_language="en-US",
                changes_not_sent_for_review=False,
                sleep_fn=sleeps.append,
            )
            self.assertTrue(result["promoted"])
            self.assertEqual(result["version_code"], "29")
            self.assertEqual(result["release_name"], "0.0.0.29")
            self.assertEqual(sleeps, [15.0])
            self.assertTrue(session.calls[-1]["url"].endswith(":commit"))

    def test_auto_promote_handles_post_upload_bump_case_b(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "APPROVED_FOR_PLAY_STORE")
            session = FakeSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "poll-1"})),
                    (
                        "GET",
                        "/edits/poll-1/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "29.0", "status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/poll-1/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "28.0", "status": "completed", "versionCodes": ["28"]}]}),
                    ),
                    ("DELETE", "/edits/poll-1", FakeResponse(204)),
                    ("POST", "/edits", FakeResponse(200, {"id": "promo-2"})),
                    ("GET", "/edits/promo-2/tracks", FakeResponse(200, {"tracks": [{"track": "internal"}, {"track": "alpha"}]})),
                    (
                        "GET",
                        "/edits/promo-2/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "29.0", "status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/promo-2/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "28.0", "status": "completed", "versionCodes": ["28"]}]}),
                    ),
                    ("PUT", "/edits/promo-2/tracks/alpha", FakeResponse(200, {"track": "alpha"})),
                    ("POST", "/edits/promo-2:commit", FakeResponse(200, {"id": "promo-2"})),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            result = mod.run_auto_promote(
                client,
                from_track="internal",
                to_track="alpha",
                current_version_code=30,
                previous_version_code=29,
                sign_off_path=sign_off,
                require_approved=True,
                poll_attempts=1,
                poll_interval_sec=0.0,
                status="completed",
                notes_source="none",
                notes_file=Path(tmpdir) / "RELEASE_NOTES.txt",
                notes_language="en-US",
                changes_not_sent_for_review=False,
            )
            self.assertTrue(result["promoted"])
            self.assertEqual(result["version_code"], "29")

    def test_auto_promote_skips_when_alpha_already_has_version(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "APPROVED_FOR_PLAY_STORE")
            session = FakeSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "poll-1"})),
                    (
                        "GET",
                        "/edits/poll-1/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/poll-1/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    ("DELETE", "/edits/poll-1", FakeResponse(204)),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            result = mod.run_auto_promote(
                client,
                from_track="internal",
                to_track="alpha",
                current_version_code=29,
                previous_version_code=28,
                sign_off_path=sign_off,
                require_approved=True,
                poll_attempts=2,
                poll_interval_sec=0.0,
                status="completed",
                notes_source="none",
                notes_file=Path(tmpdir) / "RELEASE_NOTES.txt",
                notes_language="en-US",
                changes_not_sent_for_review=False,
            )
            self.assertFalse(result["promoted"])
            self.assertIn("already serves versionCode 29", result["skip_reason"])
            self.assertEqual(session.calls[-1]["method"], "DELETE")

    def test_auto_promote_skips_when_no_newer_internal_build_after_polling(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "APPROVED_FOR_PLAY_STORE")
            session = FakeSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "poll-1"})),
                    (
                        "GET",
                        "/edits/poll-1/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/poll-1/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"status": "completed", "versionCodes": ["29"]}]}),
                    ),
                    ("DELETE", "/edits/poll-1", FakeResponse(204)),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            result = mod.run_auto_promote(
                client,
                from_track="internal",
                to_track="alpha",
                current_version_code=30,
                previous_version_code=29,
                sign_off_path=sign_off,
                require_approved=True,
                poll_attempts=1,
                poll_interval_sec=0.0,
                status="completed",
                notes_source="none",
                notes_file=Path(tmpdir) / "RELEASE_NOTES.txt",
                notes_language="en-US",
                changes_not_sent_for_review=False,
            )
            self.assertFalse(result["promoted"])
            self.assertIn("No promotable newer release", result["skip_reason"])
            self.assertEqual(session.calls[-1]["method"], "DELETE")

    def test_main_auto_promote_short_circuits_without_credentials_and_writes_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "PROMOTED_TO_CLOSED_TESTING")
            gh_out = Path(tmpdir) / "github_output.txt"
            rc = mod.main(
                [
                    "--github-output",
                    str(gh_out),
                    "auto-promote",
                    "--sign-off-file",
                    str(sign_off),
                    "--current-version-code",
                    "30",
                    "--previous-version-code",
                    "29",
                ]
            )
            self.assertEqual(rc, 0)
            output_text = gh_out.read_text(encoding="utf-8")
            self.assertIn("promoted=false\n", output_text)
            self.assertIn("PROMOTED_TO_CLOSED_TESTING", output_text)


if __name__ == "__main__":
    unittest.main()
