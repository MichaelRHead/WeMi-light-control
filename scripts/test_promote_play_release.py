#!/usr/bin/env python3
"""Unit tests for scripts/promote_play_release.py using mocked HTTP responses."""

from __future__ import annotations

import io
import json
import sys
import tempfile
import types
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from typing import Any
from unittest import mock

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

    def _write_sign_off(self, tmpdir: str, status: str, version_code: int | None = None) -> Path:
        path = Path(tmpdir) / "RELEASE_SIGN_OFF.md"
        vc_line = f"VERSION_CODE: {version_code}\n" if version_code is not None else ""
        path.write_text(
            f"<!-- RELEASE_STATE_BEGIN -->\n{vc_line}RELEASE_STATUS: {status}\nCURRENT_TURN: NONE\n<!-- RELEASE_STATE_END -->\n",
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

    def test_release_status_reads_lifecycle_without_opening_edit(self) -> None:
        session = FakeSession(
            [
                (
                    "GET",
                    "/tracks/alpha/releases",
                    FakeResponse(
                        200,
                        {
                            "releases": [
                                {
                                    "releaseName": "0.0.0.32",
                                    "track": "alpha",
                                    "activeArtifacts": [{"versionCode": 32}],
                                    "releaseLifecycleState": "RELEASE_LIFECYCLE_STATE_IN_REVIEW",
                                },
                                {
                                    "releaseName": "0.0.0.31",
                                    "track": "alpha",
                                    "activeArtifacts": [{"versionCode": 31}],
                                    "releaseLifecycleState": "RELEASE_LIFECYCLE_STATE_PUBLISHED",
                                },
                            ]
                        },
                    ),
                )
            ]
        )
        client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
        result = mod.run_release_status(client, track="alpha", version_code="32")
        self.assertEqual(result["version_code"], "32")
        self.assertEqual(result["release_name"], "0.0.0.32")
        self.assertEqual(result["lifecycle_state"], "RELEASE_LIFECYCLE_STATE_IN_REVIEW")
        self.assertFalse(result["is_terminal"])
        self.assertEqual(len(session.calls), 1)
        self.assertEqual(session.calls[0]["method"], "GET")

    def test_release_status_polls_until_published(self) -> None:
        session = FakeSession(
            [
                (
                    "GET",
                    "/tracks/alpha/releases",
                    FakeResponse(
                        200,
                        {
                            "releases": [
                                {
                                    "releaseName": "0.0.0.32",
                                    "track": "alpha",
                                    "activeArtifacts": [{"versionCode": 32}],
                                    "releaseLifecycleState": "RELEASE_LIFECYCLE_STATE_IN_REVIEW",
                                }
                            ]
                        },
                    ),
                ),
                (
                    "GET",
                    "/tracks/alpha/releases",
                    FakeResponse(
                        200,
                        {
                            "releases": [
                                {
                                    "releaseName": "0.0.0.32",
                                    "track": "alpha",
                                    "activeArtifacts": [{"versionCode": 32}],
                                    "releaseLifecycleState": "RELEASE_LIFECYCLE_STATE_PUBLISHED",
                                }
                            ]
                        },
                    ),
                ),
            ]
        )
        sleeps: list[float] = []
        client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
        result = mod.run_release_status(
            client,
            track="alpha",
            version_code="32",
            poll_attempts=3,
            poll_interval_sec=10.0,
            sleep_fn=sleeps.append,
        )
        self.assertEqual(result["lifecycle_state"], "RELEASE_LIFECYCLE_STATE_PUBLISHED")
        self.assertTrue(result["is_terminal"])
        self.assertEqual(sleeps, [10.0])

    def test_auto_promote_case_b_recovers_sign_off_version_code_without_previous_vc(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "APPROVED_FOR_PLAY_STORE", version_code=35)
            session = FakeSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "poll-b"})),
                    (
                        "GET",
                        "/edits/poll-b/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "0.0.0.35", "status": "completed", "versionCodes": ["35"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/poll-b/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "0.0.0.34", "status": "completed", "versionCodes": ["34"]}]}),
                    ),
                    ("DELETE", "/edits/poll-b", FakeResponse(204)),
                    ("POST", "/edits", FakeResponse(200, {"id": "promo-b"})),
                    ("GET", "/edits/promo-b/tracks", FakeResponse(200, {"tracks": [{"track": "internal"}, {"track": "alpha"}]})),
                    (
                        "GET",
                        "/edits/promo-b/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "0.0.0.35", "status": "completed", "versionCodes": ["35"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/promo-b/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "0.0.0.34", "status": "completed", "versionCodes": ["34"]}]}),
                    ),
                    ("PUT", "/edits/promo-b/tracks/alpha", FakeResponse(200, {"track": "alpha"})),
                    ("POST", "/edits/promo-b:commit", FakeResponse(200, {"id": "promo-b"})),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            result = mod.run_auto_promote(
                client,
                from_track="internal",
                to_track="alpha",
                current_version_code=36,
                previous_version_code=None,
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
            self.assertEqual(result["version_code"], "35")
            self.assertEqual(result["release_name"], "0.0.0.35")

    def test_promote_in_progress_preserves_existing_completed_release_on_target_track(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            existing_completed = {
                "name": "0.0.0.34",
                "status": "completed",
                "versionCodes": ["34"],
            }
            session = FakeSession(
                [
                    ("POST", "/edits", FakeResponse(200, {"id": "edit-rollout"})),
                    ("GET", "/edits/edit-rollout/tracks", FakeResponse(200, {"tracks": [{"track": "alpha"}, {"track": "production"}]})),
                    (
                        "GET",
                        "/edits/edit-rollout/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "0.0.0.35", "status": "completed", "versionCodes": ["35"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/edit-rollout/tracks/production",
                        FakeResponse(
                            200,
                            {
                                "track": "production",
                                "releases": [
                                    existing_completed,
                                    {"name": "0.0.0.33", "status": "draft", "versionCodes": ["33"]},
                                ],
                            },
                        ),
                    ),
                    ("PUT", "/edits/edit-rollout/tracks/production", FakeResponse(200, {"track": "production"})),
                    ("POST", "/edits/edit-rollout:commit", FakeResponse(200, {"id": "edit-rollout"})),
                ]
            )
            client = mod.PlayPublisherClient("com.aistudio.wemiswitch.kxpqzr", session=session)
            promoted = mod.run_promote(
                client,
                from_track="alpha",
                to_track="production",
                version_code="35",
                status="inProgress",
                user_fraction=0.2,
                notes_source="none",
                notes_file=Path(tmpdir) / "RELEASE_NOTES.txt",
                notes_language="en-US",
                release_name_override="",
                changes_not_sent_for_review=False,
                allow_same_version=False,
            )
            self.assertEqual(promoted["status"], "inProgress")
            self.assertEqual(promoted["userFraction"], 0.2)
            put_call = [c for c in session.calls if c["method"] == "PUT"][0]
            sent_releases = put_call["json"]["releases"]
            self.assertEqual(len(sent_releases), 2)
            self.assertEqual(sent_releases[0]["versionCodes"], ["35"])
            self.assertEqual(sent_releases[0]["status"], "inProgress")
            self.assertEqual(sent_releases[1], existing_completed)

    def test_main_catches_requests_request_exception_cleanly(self) -> None:
        class FakeRequestException(Exception):
            pass

        class FakeConnectionError(FakeRequestException):
            pass

        fake_requests = types.ModuleType("requests")
        fake_requests.RequestException = FakeRequestException  # type: ignore[attr-defined]

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
            stderr_buf = io.StringIO()
            with (
                mock.patch.dict(sys.modules, {"requests": fake_requests}),
                mock.patch.object(
                    mod,
                    "build_authorized_session",
                    side_effect=FakeConnectionError("Play API network timeout"),
                ),
                redirect_stderr(stderr_buf),
            ):
                rc = mod.main(["--credentials", str(key_file), "list-tracks"])

            self.assertEqual(rc, 1)
            self.assertIn("ERROR: Play API network timeout", stderr_buf.getvalue())

    def test_client_retries_transient_503_on_idempotent_requests(self) -> None:
        sleeps: list[float] = []
        session = FakeSession(
            [
                ("POST", "/edits", FakeResponse(503, {"error": {"message": "The service is currently unavailable."}})),
                ("POST", "/edits", FakeResponse(200, {"id": "edit-retry"})),
                ("GET", "/edits/edit-retry/tracks", FakeResponse(502, text="Bad Gateway")),
                ("GET", "/edits/edit-retry/tracks", FakeResponse(200, {"tracks": [{"track": "alpha", "releases": []}]})),
                ("DELETE", "/edits/edit-retry", FakeResponse(204)),
            ]
        )
        client = mod.PlayPublisherClient(
            "com.aistudio.wemiswitch.kxpqzr",
            session=session,
            max_transient_retries=2,
            retry_backoff_sec=1.5,
            sleep_fn=sleeps.append,
        )
        tracks = mod.run_list_tracks(client)
        self.assertEqual([t["track"] for t in tracks], ["alpha"])
        self.assertEqual(sleeps, [1.5, 1.5])
        self.assertEqual(len(session.calls), 5)

    def test_client_does_not_retry_commit_or_403_errors(self) -> None:
        sleeps: list[float] = []
        commit_session = FakeSession(
            [("POST", "/edits/edit-9:commit", FakeResponse(503, {"error": {"message": "Unavailable"}}))]
        )
        commit_client = mod.PlayPublisherClient(
            "com.aistudio.wemiswitch.kxpqzr",
            session=commit_session,
            max_transient_retries=3,
            retry_backoff_sec=1.0,
            sleep_fn=sleeps.append,
        )
        with self.assertRaises(mod.PlayApiError) as commit_ctx:
            commit_client.commit_edit("edit-9")
        self.assertEqual(commit_ctx.exception.status_code, 503)
        self.assertEqual(len(commit_session.calls), 1)
        self.assertEqual(sleeps, [])

        auth_session = FakeSession(
            [("GET", "/tracks/alpha/releases", FakeResponse(403, {"error": {"message": "Permission denied"}}))]
        )
        auth_client = mod.PlayPublisherClient(
            "com.aistudio.wemiswitch.kxpqzr",
            session=auth_session,
            max_transient_retries=3,
            retry_backoff_sec=1.0,
            sleep_fn=sleeps.append,
        )
        with self.assertRaises(mod.PlayApiError) as auth_ctx:
            auth_client.list_track_releases("alpha")
        self.assertEqual(auth_ctx.exception.status_code, 403)
        self.assertEqual(len(auth_session.calls), 1)
        self.assertEqual(sleeps, [])

    def test_auto_promote_continues_polling_after_transient_503(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            sign_off = self._write_sign_off(tmpdir, "APPROVED_FOR_PLAY_STORE", version_code=38)
            poll_sleeps: list[float] = []
            session = FakeSession(
                [
                    # Poll attempt 1 fails with transient 503 (per-request retries disabled to test poll loop)
                    ("POST", "/edits", FakeResponse(503, {"error": {"message": "The service is currently unavailable."}})),
                    # Poll attempt 2 succeeds and sees build 38 on internal
                    ("POST", "/edits", FakeResponse(200, {"id": "poll-2"})),
                    (
                        "GET",
                        "/edits/poll-2/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "0.0.0.38", "status": "completed", "versionCodes": ["38"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/poll-2/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "0.0.0.37", "status": "completed", "versionCodes": ["37"]}]}),
                    ),
                    ("DELETE", "/edits/poll-2", FakeResponse(204)),
                    # Promotion edit
                    ("POST", "/edits", FakeResponse(200, {"id": "promo-38"})),
                    ("GET", "/edits/promo-38/tracks", FakeResponse(200, {"tracks": [{"track": "internal"}, {"track": "alpha"}]})),
                    (
                        "GET",
                        "/edits/promo-38/tracks/internal",
                        FakeResponse(200, {"track": "internal", "releases": [{"name": "0.0.0.38", "status": "completed", "versionCodes": ["38"]}]}),
                    ),
                    (
                        "GET",
                        "/edits/promo-38/tracks/alpha",
                        FakeResponse(200, {"track": "alpha", "releases": [{"name": "0.0.0.37", "status": "completed", "versionCodes": ["37"]}]}),
                    ),
                    ("PUT", "/edits/promo-38/tracks/alpha", FakeResponse(200, {"track": "alpha"})),
                    ("POST", "/edits/promo-38:commit", FakeResponse(200, {"id": "promo-38"})),
                ]
            )
            client = mod.PlayPublisherClient(
                "com.aistudio.wemiswitch.kxpqzr",
                session=session,
                max_transient_retries=0,
            )
            result = mod.run_auto_promote(
                client,
                from_track="internal",
                to_track="alpha",
                current_version_code=38,
                previous_version_code=37,
                sign_off_path=sign_off,
                require_approved=True,
                poll_attempts=3,
                poll_interval_sec=20.0,
                status="completed",
                notes_source="none",
                notes_file=Path(tmpdir) / "RELEASE_NOTES.txt",
                notes_language="en-US",
                changes_not_sent_for_review=False,
                sleep_fn=poll_sleeps.append,
            )
            self.assertTrue(result["promoted"])
            self.assertEqual(result["version_code"], "38")
            self.assertEqual(poll_sleeps, [20.0])


if __name__ == "__main__":
    unittest.main()
