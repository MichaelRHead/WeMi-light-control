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


if __name__ == "__main__":
    unittest.main()
