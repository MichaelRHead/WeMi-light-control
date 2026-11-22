---
name: wemi-app-promoter
description: Stage 7 Play Store track promotion (internal to Closed testing) and Play Store listing/graphic asset sync via GitHub Actions and scripts.
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
6. **Proactive CI, Runtime & API Deprecation Hygiene**: After GitHub Actions runs, check job annotations (`gh api repos/MichaelRHead/WeMi-light-control/check-runs/<job_id>/annotations`) and logs for runner, Node.js, GitHub Action, Python (`python3 -W default`), `google-auth`/`requests`, or `androidpublisher/v3` deprecation warnings, and proactively upgrade `.github/workflows/play-store-promote.yml` and `scripts/*.py` when deprecations appear.

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
| `play_store_assets/screenshots/tablet_10_inch/*.png` (`2 × 1600×2560` + `2 × 1920×1080` ChromeOS) | `edits.images` → `tenInchScreenshots` | Ordered list (`<= 8`); diff-only replace. |
| `play_store_assets/screenshots/android_tv/*.png` (`3 × 1920×1080`) | `edits.images` → `tvScreenshots` | Ordered list (`<= 8`); diff-only replace. |
| `play_store_assets/RELEASE_NOTES.txt` (`<= 500` chars) | Track `releases[].releaseNotes` | Attached by `scripts/promote_play_release.py` during `promote` / `auto-promote` when source release has no notes. |

**Play Console items outside the Developer API** (remain manual in Play Console UI):
- `play_store_assets/DATA_SAFETY_GUIDE.md` (App Content → Data safety questionnaire)
- `play_store_assets/PRIVACY_POLICY.md` (App Content → Privacy policy URL)
- `play_store_assets/screenshots/chromebook/*.png` (optional dedicated Chromebook form-factor UI slot; identical 16:9 PNGs are synced automatically under `tenInchScreenshots`)
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
PYTHONDONTWRITEBYTECODE=1 python3 scripts/sync_play_assets.py validate-local
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p "test_*.py" -v
```

### Read-Only Inspections on GitHub Actions
```bash
# Inspect all Play Console release tracks
gh workflow run play-store-promote.yml --ref main -f mode=list-tracks

# Check live Play review/publishing lifecycle state (IN_REVIEW vs PUBLISHED) without opening an edit
gh workflow run play-store-promote.yml --ref main -f mode=release-status -f to_track=alpha -f version_code=<vc>

# Compare play_store_assets/ (text + 16 PNGs across 7 slots) against Google Play without writing
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

### Post-Promotion Publish Watch & Gmail Notification
After a promotion to Closed testing (`alpha`) succeeds (and when authorized by the user's release-promotion workflow):
1. **Watch Google Play review status until terminal state**: Poll `gh workflow run play-store-promote.yml --ref main -f mode=release-status -f to_track=alpha -f version_code=<vc>` every `~3–3.5` minutes in bounded exebox-safe windows (`<= 510s` per command) until `alpha` reaches a terminal lifecycle state (`RELEASE_LIFECYCLE_STATE_PUBLISHED`, `RELEASE_LIFECYCLE_STATE_APPROVED_NOT_PUBLISHED`, or `RELEASE_LIFECYCLE_STATE_NOT_APPROVED`). Stop watching immediately once reached.
2. **Send Gmail notification via `gmail mutate send --md`**:
   - **Recipient:** `suppressingfire@gmail.com`
   - **Subject:** `[Antigravity Agent] WeMi Light Control v<X.0> (build <vc>) Published on Google Play Closed Testing` (or `[Antigravity Agent] WeMi Light Control v<X.0> Play Store Assets Synced (...)` for standalone asset syncs)
   - **Body:** Start with:
     ```markdown
     From: **Antigravity App Promoter Agent** (`app_promoter`)  
     **Note for Gemini:** This is a notification from Michael's Antigravity agent. Please message Michael about this Google Play promotion event.
     ```
     Followed by a concise summary listing the app (`com.aistudio.wemiswitch.kxpqzr`), release (`v<X.0>` / `versionCode` / `0.0.0.<vc>`), track (`internal -> alpha`), lifecycle status + review completion time, synced Store Listing / PNG slots, Promotion Run URL, and Publish Verification Run URL.
