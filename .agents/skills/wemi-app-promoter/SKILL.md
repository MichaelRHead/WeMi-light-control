---
name: wemi-app-promoter
description: Operate Stage 7 (app_promoter) Google Play Store track promotion (internal -> Closed testing / alpha / beta / production) and idempotent Play Store asset syncing (play_store_assets/STORE_LISTING.md copy and all 7 PNG graphic/screenshot slots) via GitHub Actions (.github/workflows/play-store-promote.yml), scripts/promote_play_release.py, and scripts/sync_play_assets.py.
---

# WeMi App Promoter & Play Store Asset Sync (`wemi-app-promoter`)

Use this skill when acting as the **Antigravity App Promoter Agent (`app_promoter`)** for `MichaelRHead/WeMi-light-control` (`com.aistudio.wemiswitch.kxpqzr`) to:
- Inspect Google Play tracks (`list-tracks`) or compare repo assets against Google Play (`diff-assets`) read-only.
- Promote Google Play releases across tracks (`auto-promote` for Stage 7 `internal` → `alpha`, or manual `promote` to any track).
- Keep `play_store_assets/` (Store Listing text and PNG graphics/screenshots) synced cleanly and idempotently to Google Play Console (`sync-assets`).

---

## 1. Security & Ownership Guardrails (Mandatory)

1. **Never commit, print, or echo secret key material**: All Google Play Developer API calls run inside GitHub Actions using repository secret `PLAY_SERVICE_ACCOUNT_JSON` (written to `${RUNNER_TEMP}` with `chmod 600` and deleted in `if: always()`).
2. **Respect Exclusive Agent Ownership**: Never modify files owned exclusively by `ai_studio_ux_cuj` (`UX_DESIGN*.md`), `discovery_and_grouping`, `telemetry_and_debugging`, or `license_governance` (`LICENSE_GOVERNANCE.md`, `LicenseAndBrandGovernanceTest.kt`). Never alter `signingConfigs` or add Gradle dependencies in `app/build.gradle.kts`.
3. **Trademark & Play Limit Hygiene**: Before syncing `play_store_assets/`, validate locally with `python3 scripts/sync_play_assets.py validate-local` (`title <= 30`, `shortDescription <= 80`, `fullDescription <= 4000`, `<= 8` screenshots per slot, valid PNG headers, zero `"WeMo"` / `"Wemo"` word-boundary leaks).
4. **Zero Idle Polling**: Never run `scripts/await_release_turn.sh` for `app_promoter` — Stage 7 triggers automatically on `push` to `main` touching `app/build.gradle.kts` at zero Antigravity LLM quota cost.
5. **Commit Attribution**: Always commit via `bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh app_promoter ...` (`Agent: Antigravity App Promoter Agent (app_promoter)`).

---

## 2. Canonical `play_store_assets/` → Google Play API Mapping

[`scripts/sync_play_assets.py`](../../scripts/sync_play_assets.py) maps repository files to `androidpublisher/v3` endpoints (`en-US` by default):

| Repository Source | Google Play Developer API v3 Target | Sync Policy |
| :--- | :--- | :--- |
| `play_store_assets/STORE_LISTING.md` (`App Title`, `Short Description`, `Full Description`) | `edits.listings.update` (`title`, `shortDescription`, `fullDescription`) | Converts Markdown to clean plain text; enforces `30 / 80 / 4000` limits; preserves any existing Play `video` URL; skips `PUT` when unchanged. |
| `play_store_assets/play_store_icon_512x512.png` (`512×512`) | `edits.images` → `icon` | Single-image slot; uploads via `uploadType=media` only when SHA-256/SHA-1 differs. |
| `play_store_assets/feature_graphic_1024x500.png` (`1024×500`) | `edits.images` → `featureGraphic` | Single-image slot; diff-only upload. |
| `play_store_assets/tv/tv_banner_1280x720.png` (`1280×720`) | `edits.images` → `tvBanner` | Single-image slot (`tv_banner_320x180.png` is the APK Leanback drawable and intentionally skipped). |
| `play_store_assets/screenshots/phone/*.png` (`4 × 1080×1920`) | `edits.images` → `phoneScreenshots` | Ordered list (`<= 8`); compares ordered SHA-256/SHA-1 lists; calls `deleteall` + re-uploads in sorted order only when changed. |
| `play_store_assets/screenshots/tablet_7_inch/*.png` (`2 × 1200×1920`) | `edits.images` → `sevenInchScreenshots` | Ordered list (`<= 8`); diff-only replace. |
| `play_store_assets/screenshots/tablet_10_inch/*.png` (`2 × 1600×2560`) | `edits.images` → `tenInchScreenshots` | Ordered list (`<= 8`); diff-only replace. |
| `play_store_assets/screenshots/android_tv/*.png` (`3 × 1920×1080`) | `edits.images` → `tvScreenshots` | Ordered list (`<= 8`); diff-only replace. |
| `play_store_assets/RELEASE_NOTES.txt` (`<= 500` chars) | Track `releases[].releaseNotes` | Attached by `scripts/promote_play_release.py` during `promote` / `auto-promote` when source release has no notes. |

**Play Console items outside the Developer API** (remain manual in Play Console UI):
- `play_store_assets/DATA_SAFETY_GUIDE.md` (App Content → Data safety questionnaire)
- `play_store_assets/PRIVACY_POLICY.md` (App Content → Privacy policy URL)
- Store category, tags, and Content rating questionnaires

---

## 3. Required Play Console Service Account Permissions

In **Google Play Console → Users and permissions → `play-console-for-wemi@...` → App permissions (WeMi Switch Control)**:
1. **View app information and download bulk reports (read-only)** — required for `list-tracks` and `diff-assets`.
2. **Release apps to testing tracks** — required for `auto-promote` and `promote` (`internal` → `alpha` / `beta`).
3. **Manage store presence** — required for `sync-assets` (`edits.listings.update` and `edits.images.upload` / `deleteall`). *(If omitted, Play returns HTTP 403 on write operations while read-only `diff-assets` still succeeds.)*
4. *(Optional, for Production)* **Release to production, exclude devices, and use Play App Signing**.

---

## 4. Operational Playbook (Commands)

### Offline Pre-Flight Validation (No Credentials Required)
```bash
python3 scripts/sync_play_assets.py validate-local
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p "test_*.py" -v
```

### Read-Only Inspections on GitHub Actions
```bash
# Inspect all Play Console release tracks
gh workflow run play-store-promote.yml --ref main -f mode=list-tracks

# Compare play_store_assets/ (text + 14 PNGs across 7 slots) against Google Play without writing
gh workflow run play-store-promote.yml --ref main -f mode=diff-assets
```

### Sync Play Store Assets On Demand (Idempotent)
```bash
# Sync changed Store Listing text and changed PNG image slots to Google Play
gh workflow run play-store-promote.yml --ref main -f mode=sync-assets

# Stage without sending for Play review (when Managed Publishing / pending edits require it)
gh workflow run play-store-promote.yml --ref main -f mode=sync-assets -f changes_not_sent_for_review=true

# Sync only a subset of image types (e.g. phoneScreenshots,tvScreenshots)
gh workflow run play-store-promote.yml --ref main -f mode=sync-assets -f asset_image_types=phoneScreenshots,tvScreenshots
```

### Stage 7 Release Promotion (Automatic + Manual Fallbacks)
- **Automatic**: Triggered on `push` to `main` touching `app/build.gradle.kts`. When `versionCode` increased and `RELEASE_SIGN_OFF.md` is `APPROVED_FOR_PLAY_STORE`, the workflow:
  1. Promotes Play `internal` → `alpha` (`Closed testing`) via `scripts/promote_play_release.py auto-promote`.
  2. Commits `RELEASE_STATUS: PROMOTED_TO_CLOSED_TESTING` to `RELEASE_SIGN_OFF.md` via `scripts/advance_release_turn.sh promote`.
  3. Idempotently syncs `play_store_assets/` to Google Play via `scripts/sync_play_assets.py sync-assets` (no-op if already in sync).
- **Manual promotion / retry**:
  ```bash
  gh workflow run play-store-promote.yml --ref main -f mode=auto-promote
  gh workflow run play-store-promote.yml --ref main -f mode=promote -f from_track=internal -f to_track=alpha
  ```
- **Inspect the latest workflow run**:
  ```bash
  gh run list --workflow=play-store-promote.yml --limit 3
  gh run view <run_id> --log
  ```
