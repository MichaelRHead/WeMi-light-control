---
name: agent-commit-attribution
description: "Mandatory Git commit message formatting, human-readability, and attribution convention for all agents. Enforces plain-English <=72-char Conventional Commits subjects (no '[...Agent]' tags), concise reflowable plain-text bodies written for humans (what changed and why, no diff narration or symbol dumps, <=260 chars/bullet, <=1000 chars total), brief 'Verified:' trailers (<=120 chars), and mandatory 'Agent: <Canonical Agent Name> (<role_id>)' trailers. Use before every git commit, git commit --amend, automated release script commit, or AI Studio GitHub export."
---

# Agent Commit Attribution & Human-Readable Formatting Skill (`agent-commit-attribution`)

Use this skill **before creating any Git commit** (via `git commit`, helper scripts, or Google AI Studio's GitHub export UI) in `MichaelRHead/WeMi-light-control` or any multi-agent repository.

---

## 1. Core Principles (Write for a Human Reader, Not a Compiler)

A person scanning `git log` or GitHub on a phone should understand **what changed and why in 5 seconds**. Git already shows every modified file and code diff — repeating every class name, method signature, Compose test tag, line count, and Gradle flag inside a single breathless sentence makes commit history inscrutable.

**Mandatory Invariants**:
1. **Plain-English subject line (`<= 72` chars, ideally `<= 55`; no `[...Agent]` tag)**:
   - Use Conventional Commits: `<type>(<scope>): <plain-English outcome>` (or `<type>: <summary>`).
   - State the real-world change clearly (e.g., `fix(discovery): stop mixing up switches when IPs change`), with no trailing `.`, no `[...Agent]` tag, and no raw snake_case role IDs.
2. **Short, plain-English body explaining *what changed* and *why***:
   - Include a blank line after the subject, then a brief plain-English summary (1 short opening sentence and/or 1–4 concise bullets).
   - **No symbol salads or diff narration**: Don't cram lists of filenames, function signatures, test tags, file line counts (`max=495L`), or octal modes (`100755`) into prose. Name a file or tool only when needed to understand *why* (aim for at most 1–2 names per bullet; validator rejects `>= 8` code tokens on one line).
   - **Short, direct sentences**: One idea per bullet (`<= 260` chars per paragraph/bullet, total body `<= 1,000` chars). Never chain clauses with multiple semicolons (`; ...; ...`) or nested parentheses (`(foo (bar))`).
   - **Plain text & reflowable lines**: GitHub renders commit pages as plain monospace text (`<pre>` with `white-space: pre-wrap`), not Markdown. Omit `**bold**`, `` `backticks` ``, and `#` headings (reserve Markdown for PRs, Issues, and Release Notes), and do not hard-wrap bullets/paragraphs at 72 columns (so mobile GitHub reflows cleanly without zig-zag breaks). Separate longer bullets with a blank line.
3. **Brief `Verified:` trailer & mandatory `Agent:` trailer**:
   - End every commit with a blank line followed by a Git trailer block:
     - Optional `Verified: <brief plain summary>` (`<= 120` chars — e.g., `Verified: 73/73 tests passing, lint clean, debug APK builds`). Don't repeat test counts inside the body.
     - Mandatory `Agent: <Canonical Agent Name> (<role_id>)` trailer.
   - Standard Git trailers stay machine-queryable via `git log --format="%h %s | %(trailers:key=Agent,valueonly)"`.

---

## 2. Canonical Agent Names & Role IDs

Every agent must identify itself in the `Agent:` trailer using its canonical name and role ID:

| Stage / Domain | Role ID | Canonical `Agent:` Trailer |
| :--- | :--- | :--- |
| **UX, Design, Assets & Release Init/Conclude** | `ai_studio_ux_cuj` | `Agent: Google AI Studio Agent (ai_studio_ux_cuj)` |
| **SSDP/Unicast Discovery, Identity & Room Grouping** | `discovery_and_grouping` | `Agent: Antigravity Device Discovery & Room Grouping Agent (discovery_and_grouping)` |
| **10.22.22.1 Setup Telemetry, SOAP Logs & Support** | `telemetry_and_debugging` | `Agent: Antigravity Telemetry & Debugging Agent (telemetry_and_debugging)` |
| **Modularity, Build Hygiene & Hermetic/E2E Testing** | `code_quality_and_testing` | `Agent: Antigravity Code Quality & E2E Testing Agent (code_quality_and_testing)` |
| **MIT License Parity, Dependencies & Brand Hygiene** | `license_governance` | `Agent: Antigravity License & Brand Governance Agent (license_governance)` |
| *(General / Cross-Repo Task Agent)* | `antigravity_general` | `Agent: Antigravity General Agent (antigravity_general)` |

---

## 3. Required Commit Message Format

```text
<type>(<scope>): <plain-English summary <= 72 chars>

Optional 1-sentence context explaining the problem or motivation in everyday language.

- Plain-English bullet explaining one meaningful change and why it matters.

- Second concise bullet (separate longer bullets with a blank line for mobile readability).

Verified: <brief verification summary <= 120 chars>
Agent: <Canonical Agent Name> (<role_id>)
```

### Before vs. After (Why This Matters)

**Avoid (Inscrutable — symbol salad, semicolon chains, nested parens, wall of text):**
```text
fix(discovery): harden 4-pass identity, unicast fallback & SSID sync

- Canonical Tier-2 <UDN> identity: add LanRuntimeUtils.expectedHardwareIdentity(serialNumber, udn) and wire it into DeviceEntity.concurrencyKey(), NetworkClient.probeWemoPort / executeWithPortHealing, SsdpDiscoveryEngine Stage B, and DiscoveryCoordinator.attemptTwoStageReconnect; reject conflicting canonical UDNs immediately in DeviceEntity.matchesDiscovered; align manual-IP saveDevice Tier-2 UDN deduplication when either serial is blank/placeholder.

Verified: 73/73 tests passing across 15 suites (--rerun-tasks, incl. LicenseAndBrandGovernanceTest & DiscoveryAndGroupingIntegrationTest), 0 lintDebug errors, assembleDebug green, 79/79 .kt files <500L, 100755 script modes
Agent: Antigravity Device Discovery & Room Grouping Agent (discovery_and_grouping)
```

**Prefer (Human-Readable — clear in 5 seconds on desktop or mobile):**
```text
fix(discovery): stop mixing up switches when IPs change

Some switches report blank or placeholder serial numbers, which could match a switch to the wrong device after a router assigns new IP addresses.

- Fall back to each switch's unique UPnP identifier (UDN) when its serial number is missing or a placeholder.

- Limit concurrent discovery lookups so low-memory switches are not overwhelmed, and scan the local subnet if multicast discovery returns nothing.

- Keep saved home Wi-Fi names intact when a switch reports its temporary setup hotspot name instead.

Verified: 73/73 tests passing, lint clean, debug APK builds
Agent: Antigravity Device Discovery & Room Grouping Agent (discovery_and_grouping)
```

**Good (Small Focused Commit):**
```text
fix(setup): give clearer advice when Wi-Fi setup fails on long passwords

- Show tailored troubleshooting hints for short vs. long Wi-Fi passwords when the switch rejects credentials during setup.

- Clear cached setup details after provisioning finishes so the next setup starts fresh.

Verified: 68/68 tests passing, lint clean, debug APK builds
Agent: Antigravity Telemetry & Debugging Agent (telemetry_and_debugging)
```

### Quick Style & Validator Limits
- **Allowed `<type>` prefixes**: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `style`, `build`, `ci`, `release`.
- **Subject**: `<= 72` chars (aim for `<= 55`), imperative, plain English, no trailing `.`, no `[...Agent]` tag.
- **Body lines**: `<= 260` chars per paragraph/bullet (aim for `<= 180`), `< 2` semicolons per line, no nested parentheses, `< 8` filenames/code symbols per line, total body `<= 1,000` chars.
- **Plain text**: No `**bold**`, `` `backticks` ``, or `#` headings in commit bodies (GitHub auto-links short SHAs like `6714062`, `#123`, `@mentions`, and bare URLs automatically). Reserve Markdown for PR descriptions, Issues, and Release Notes.
- **Verified trailer**: Optional (omit when no check applies), `<= 120` chars.

---

## 4. How to Commit in Each Environment

### 4.1 Antigravity Agents (Linux VM CLI)

#### Option A — Recommended Wrapper Script (`commit_as_agent.sh`)
Stage your files (`git add ...`) and run [`scripts/commit_as_agent.sh`](scripts/commit_as_agent.sh) (or `~/.gemini/skills/agent-commit-attribution/scripts/commit_as_agent.sh` in other repos). It strips any accidental `[...Agent]` subject tag, unfolds wrapped continuation lines into clean reflowable paragraphs/bullets (adding blank lines between longer bullets), appends `Verified:` + `Agent:` trailers, and runs all readability & format checks before committing:

```bash
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  code_quality_and_testing \
  "fix(test): run Android TV UI tests on a single SDK version" \
  --body-file - \
  --verified "68/68 tests passing, lint clean, debug APK builds" <<'EOF'
Mixing SDK versions in Robolectric caused intermittent zip-filesystem crashes when running the Android TV layout tests.

- Standardize the Android TV UI test suite on SDK 36 so runs stay deterministic.
EOF
```

Short inline usage (also supported):

```bash
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  discovery_and_grouping \
  "docs(discovery): explain UDN fallback when switch IPs change" \
  "- Document how switch discovery falls back to UPnP UDN matching after a router lease renewal." \
  --verified "Discovery unit tests passing"
```

Supported flags:
- `-v, --verified "<text>"`: adds a `Verified: <text>` trailer above `Agent:` (or pass as 4th positional argument).
- `-F, --body-file <path|->`: reads multiline body from a file or stdin (`-`).
- `--amend`: amends `HEAD` (`git commit --amend`).
- `--dry-run`: prints the formatted, validated message without committing.

#### Option B — Direct `git commit` with Heredoc
```bash
git commit -F - <<'EOF'
docs(discovery): explain UDN fallback when switch IPs change

- Document how switch discovery falls back to UPnP UDN matching after a router lease renewal.

Verified: Discovery unit tests passing
Agent: Antigravity Device Discovery & Room Grouping Agent (discovery_and_grouping)
EOF
```

#### Option C — Install the Local `commit-msg` Git Hook
Run once in any repository workspace so Git enforces readability, structure, and `Agent:` attribution automatically:

```bash
bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh --install-hook
```

### 4.2 Google AI Studio Agent (GitHub Export / Sync UI)

When exporting or committing changes to GitHub from Google AI Studio:
1. **Commit summary / message line** (`<= 72` chars, plain English, **no `[Google AI Studio Agent]` tag**):
   ```text
   feat(ui): give dimmer sliders more breathing room on phones
   ```
2. **Commit description field** (plain-English bullets + `Verified:` + `Agent:` trailer):
   ```text
   - Adjust dimmer slider padding so brightness controls stay easy to tap (at least 48dp) on both phones and tablets.

   Verified: 68/68 tests passing
   Agent: Google AI Studio Agent (ai_studio_ux_cuj)
   ```

### 4.3 Automated Release State-Machine Commits (`scripts/advance_release_turn.sh`)

[`scripts/advance_release_turn.sh`](../../../scripts/advance_release_turn.sh) generates plain-English subjects (e.g., `chore(release): sign off v28.0 discovery review`), states the stage and handoff clearly in the commit body, accepts `--verified "<brief checks>"`, and keeps verbose audit checklists in `RELEASE_SIGN_OFF.md` instead of dumping them into `git log`.

---

## 5. Pre-Push Verification Checklist

Before running `git push origin main`:
1. Read your outgoing commit message out loud (`git log -n 2 --format=fuller`):
   - Would a teammate who didn't write the code understand *what changed* and *why* in 5 seconds?
   - Is the subject `<= 72` chars in plain English with no `[...Agent]` tag or trailing `.`?
   - Are bullets short, plain English, free of raw Markdown punctuation and symbol dumps, and separated by blank lines when multi-clause?
   - Are `Verified:` (`<= 120` chars, when applicable) and `Agent: <Canonical Agent Name> (<role_id>)` cleanly placed in the bottom trailer block?
2. Validate `HEAD` directly:
   ```bash
   bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh <(git log -1 --pretty=%B)
   ```
3. Query commits by agent using native Git trailer formatting:
   ```bash
   git log -n 10 --format="%h %s | %(trailers:key=Agent,valueonly)"
   ```
