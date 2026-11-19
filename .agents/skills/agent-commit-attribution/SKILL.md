---
name: agent-commit-attribution
description: "Mandatory Git commit message formatting and attribution convention for all agents. Enforces concise <=72-char Conventional Commits subjects (with no '[...Agent]' subject tag), 72-column wrapped/bulleted commit bodies, optional 'Verified:' trailers, and mandatory 'Agent: <Canonical Agent Name> (<role_id>)' trailers. Use before every git commit, git commit --amend, automated release script commit, or AI Studio GitHub export."
---

# Agent Commit Attribution & Formatting Skill (`agent-commit-attribution`)

Use this skill **before creating any Git commit** (via `git commit`, helper scripts, or Google AI Studio's GitHub export UI) in `MichaelRHead/WeMi-light-control` or any multi-agent repository.

---

## 1. Core Principles

All collaborating agents push commits using the repository owner's GitHub credentials (`Author: Michael R. Head`). Putting long `[...Agent]` tags in commit subjects eats 30–35 characters of GitHub's 72-character subject width and causes truncation, while single-line walls of text make `git log` hard to scan.

**Mandatory Invariants**:
1. **Concise `<= 72`-character subject line (no `[...Agent]` tag in subject)**:
   - Use Conventional Commits: `<type>(<scope>): <concise imperative summary>` (or `<type>: <summary>`).
   - Keep the subject `<= 72` characters with no trailing period `.` and no trailing `[...Agent]` bracket tag.
2. **Readable, wrapped/bulleted description body (no bare one-liners)**:
   - Always include a blank line after the subject, followed by concise `- ` bullets or short paragraphs wrapped at **`<= 72` characters** per line explaining *what* changed and *why*.
3. **Trailer-only agent attribution & clean verification details**:
   - End every commit with a blank line followed by a Git trailer block containing:
     - Optional `Verified: <concise test/build verification>` trailer.
     - Mandatory `Agent: <Canonical Agent Name> (<role_id>)` trailer.
   - Standard Git trailers remain machine-queryable via `git log --format="%h %s | %(trailers:key=Agent,valueonly)"`.

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
<type>(<scope>): <concise imperative summary <= 72 chars>

- <concise change bullet wrapped at <= 72 chars explaining what and why>
- <additional bullet if needed>

Verified: <concise test/build verification summary>
Agent: <Canonical Agent Name> (<role_id>)
```

- Allowed `<type>` prefixes: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `style`, `build`, `ci`, `release`.
- Omit `Verified:` only when no verification command applies (e.g., pure text edits where noted in the body).
- Keep prose and bullet lines wrapped at `<= 72` chars (`validate_commit_msg.sh` rejects unwrapped prose lines `> 100` chars).

### Example (Good)

```text
fix(telemetry): harden Status=2 diagnostics and sanitized log export

- Use descriptor-driven recommendedAddLengths and split Status=2 guidance
  for <8-char vs. >=8-char passwords while preserving CloseSetup hold.
- Clear cached setup descriptors after provisioning and fast-skip
  unreachable AP scans.
- Scrub <password> XML tags in sanitizeXmlSnippet and restore +x modes.

Verified: 68/68 tests (--rerun-tasks), lintDebug (0 errors), assembleDebug
Agent: Antigravity Telemetry & Debugging Agent (telemetry_and_debugging)
```

### Avoid (Bad)
- `fix(telemetry): harden v28.0 Status=2 diagnostics, cache reset & sanitized export [Telemetry & Debugging Agent]` *(111 chars — truncated on GitHub; redundant `[...Agent]` subject tag)*
- A 600-character unwrapped single-line body cramming 8 changes and verification counts into one run-on sentence.
- Bare one-line commits (`git commit -m "fix bug"`) or trailer-only commits with no description body.

---

## 4. How to Commit in Each Environment

### 4.1 Antigravity Agents (Linux VM CLI)

#### Option A — Recommended Wrapper Script (`commit_as_agent.sh`)
Stage your files (`git add ...`) and run [`scripts/commit_as_agent.sh`](scripts/commit_as_agent.sh) (or `~/.gemini/skills/agent-commit-attribution/scripts/commit_as_agent.sh` in other repos). It strips any accidental `[...Agent]` subject tag, auto-wraps paragraphs and bullets at `72` columns with hanging indentation, formats `Verified:` + `Agent:` trailers, and validates before committing:

```bash
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  code_quality_and_testing \
  "fix(test): align Android TV Robolectric test to SDK 36" \
  --body-file - \
  --verified "68/68 tests (--rerun-tasks), lintDebug, assembleDebug" <<'EOF'
- Standardize @Config(sdk = [36], qualifiers = "television") in
  TvAndHeaderLayoutE2ETest to prevent dual-SDK ZipFileSystemProvider
  collisions.
EOF
```

Short inline usage (also supported):

```bash
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  discovery_and_grouping \
  "docs(discovery): clarify 3-tier UDN fallback on DHCP renewal" \
  "Clarify <serialNumber> to <UDN> fallback behavior during DHCP lease renewal in DISCOVERY_AND_ROOM_GROUPING.md." \
  --verified "SkuAndCryptoTest & SoapAndSelfHealingTest passing"
```

Supported flags:
- `-v, --verified "<text>"`: adds a `Verified: <text>` trailer above `Agent:` (or pass as 4th positional argument).
- `-F, --body-file <path|->`: reads multiline body from a file or stdin (`-`).
- `--amend`: amends `HEAD` (`git commit --amend`).
- `--dry-run`: prints the formatted, validated message without committing.

#### Option B — Direct `git commit` with Heredoc
```bash
git commit -F - <<'EOF'
docs(discovery): clarify 3-tier UDN fallback on DHCP renewal

- Clarify <serialNumber> to <UDN> fallback behavior during DHCP lease
  renewal in DISCOVERY_AND_ROOM_GROUPING.md.

Verified: SkuAndCryptoTest & SoapAndSelfHealingTest passing
Agent: Antigravity Device Discovery & Room Grouping Agent (discovery_and_grouping)
EOF
```

#### Option C — Install the Local `commit-msg` Git Hook
Run once in any repository workspace so Git enforces subject length, body readability, and `Agent:` attribution automatically:

```bash
bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh --install-hook
```

### 4.2 Google AI Studio Agent (GitHub Export / Sync UI)

When exporting or committing changes to GitHub from Google AI Studio:
1. **Commit summary / message line** (`<= 72` chars, **no `[Google AI Studio Agent]` tag**):
   ```text
   feat(ui): refine dimmer brightness slider spacing
   ```
2. **Commit description field** (wrapped bullets + `Verified:` + `Agent:` trailer):
   ```text
   - Adjust top-first dimmer slider padding in DeviceRow.kt to preserve
     48dp touch targets on phone and tablet layouts.

   Verified: 68/68 tests passing across 14 suites
   Agent: Google AI Studio Agent (ai_studio_ux_cuj)
   ```

### 4.3 Automated Release State-Machine Commits (`scripts/advance_release_turn.sh`)

[`scripts/advance_release_turn.sh`](../../../scripts/advance_release_turn.sh) maps `initiate`, `sign_off <role>`, `conclude`, and `request_rereview <from_role> <target_role>` to concise `<= 58`-character Conventional Commits subjects (`chore(release): ...`) and invokes `commit_as_agent.sh` to wrap the verification summary at `72` columns and append the canonical `Agent:` trailer.

---

## 5. Pre-Push Verification Checklist

Before running `git push origin main`:
1. Inspect outgoing commit(s) with `git log -n 2 --format=fuller`:
   - Is the subject line `<= 72` chars, imperative Conventional Commits, with no `[...Agent]` tag or trailing `.`?
   - Is the body wrapped cleanly (`<= 72` chars) with clear bullets or short paragraphs?
   - Does the bottom trailer block include `Agent: <Canonical Agent Name> (<role_id>)` (and `Verified:` when applicable)?
2. Validate `HEAD` directly:
   ```bash
   bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh <(git log -1 --pretty=%B)
   ```
3. Query commits by agent using native Git trailer formatting:
   ```bash
   git log -n 10 --format="%h %s | %(trailers:key=Agent,valueonly)"
   ```
