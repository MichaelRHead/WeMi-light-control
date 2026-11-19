---
name: agent-commit-attribution
description: "Mandatory Git commit message formatting and attribution convention for all agents. Enforces concise <=72-char Conventional Commits subjects (with no '[...Agent]' subject tag), Git-safe Markdown commit bodies wrapped at 72 columns (inline code backticks, bullets, bold section headings), optional 'Verified:' trailers, and mandatory 'Agent: <Canonical Agent Name> (<role_id>)' trailers. Use before every git commit, git commit --amend, automated release script commit, or AI Studio GitHub export."
---

# Agent Commit Attribution & Formatting Skill (`agent-commit-attribution`)

Use this skill **before creating any Git commit** (via `git commit`, helper scripts, or Google AI Studio's GitHub export UI) in `MichaelRHead/WeMi-light-control` or any multi-agent repository.

---

## 1. Core Principles

All collaborating agents push commits using the repository owner's GitHub credentials (`Author: Michael R. Head`). Putting long `[...Agent]` tags in commit subjects eats 30–35 characters of GitHub's 72-character subject width and causes truncation, while single-line walls of text make `git log` and GitHub commit views hard to scan.

**Mandatory Invariants**:
1. **Concise `<= 72`-character subject line (no `[...Agent]` tag in subject)**:
   - Use Conventional Commits: `<type>(<scope>): <concise imperative summary>` (or `<type>: <summary>`).
   - Keep the subject `<= 72` characters with no trailing period `.` and no trailing `[...Agent]` bracket tag.
2. **Readable Git-safe Markdown description body (no bare one-liners)**:
   - Always include a blank line after the subject, followed by Git-safe Markdown (`- ` bullets, `` `inline code` ``, `**Bold**` labels/headings, fenced code blocks when helpful) wrapped at **`<= 72` characters** per line explaining *what* changed and *why*.
   - **Avoid `#` Markdown headings** (`#`, `##`, `###`) in commit bodies — Git strips lines starting with `#` as comments when editing messages. Use `**Section Title**` instead.
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

## 3. Required Commit Message Format & Git-Safe Markdown

```text
<type>(<scope>): <concise imperative summary <= 72 chars>

**Summary** (optional bold section label for multi-area commits)
- **Area**: concise bullet with `symbols`, `File.kt`, and `--flags`
  wrapped cleanly at <= 72 chars explaining what changed and why.
  - Optional 2-space indented sub-bullet for secondary details.

Verified: <concise test/build verification summary>
Agent: <Canonical Agent Name> (<role_id>)
```

### Recommended Git-Safe Markdown Conventions (for GitHub & CLI Readability)
- **Inline code (`` `...` ``)**: Wrap filenames (`` `DeviceRow.kt` ``), symbols (`` `executeOptimisticDeviceCommand` ``), CLI flags (`` `--rerun-tasks` ``), and protocol tokens (`` `<password>` ``) in backticks.
- **Bulleted lists (`- ` and `  - `)**: Prefer scannable bullets (with optional `**Bold Lead-In**:` labels) over dense paragraphs.
- **Bold headings (`**Section**`) — never `# Heading`**: Use `**Changes**` / `**Why**` instead of `#` / `##` headings so Git never strips them as comment lines.
- **Fenced code blocks (` ``` `)** & **blockquotes (`> `)**: Use sparingly for short commands, error snippets, or callouts (`commit_as_agent.sh` preserves fenced/indented code blocks verbatim and wraps blockquotes at 72 cols).
- **GitHub references**: Reference short commit SHAs (e.g., `cab0e4b`), issues/PRs (`#123`), or URLs directly so GitHub auto-links them.
- **Allowed `<type>` prefixes**: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `style`, `build`, `ci`, `release`.
- **Line width**: Wrap prose and bullets at `<= 72` chars (`validate_commit_msg.sh` rejects unwrapped prose lines `> 100` chars). Omit `Verified:` only when no verification step applies.

### Example (Good)

```markdown
fix(telemetry): harden Status=2 diagnostics and sanitized log export

- **Provisioning**: Use descriptor-driven `recommendedAddLengths` and
  split `Status=2` guidance for `<8`-char vs. `>=8`-char passwords
  while preserving `CloseSetup` hold.
- **Cache & Scanning**: Clear cached setup descriptors after
  provisioning and fast-skip unreachable AP scans.
- **Export Hygiene**: Scrub `<password>` XML tags in
  `sanitizeXmlSnippet` and restore `+x` script modes after `3344810`.

Verified: 68/68 tests (--rerun-tasks), lintDebug (0 errors), assembleDebug
Agent: Antigravity Telemetry & Debugging Agent (telemetry_and_debugging)
```

### Avoid (Bad)
- `fix(telemetry): harden v28.0 Status=2 diagnostics, cache reset & sanitized export [Telemetry & Debugging Agent]` *(111 chars — truncated on GitHub; redundant `[...Agent]` subject tag)*
- Using `## Changes` headings *(stripped by Git as comments)*
- A 600-character unwrapped single-line body cramming 8 changes and verification counts into one run-on sentence.
- Bare one-line commits (`git commit -m "fix bug"`) or trailer-only commits with no description body.

---

## 4. How to Commit in Each Environment

### 4.1 Antigravity Agents (Linux VM CLI)

#### Option A — Recommended Wrapper Script (`commit_as_agent.sh`)
Stage your files (`git add ...`) and run [`scripts/commit_as_agent.sh`](scripts/commit_as_agent.sh) (or `~/.gemini/skills/agent-commit-attribution/scripts/commit_as_agent.sh` in other repos). It strips any accidental `[...Agent]` subject tag, auto-wraps Markdown paragraphs, bullets, nested sub-bullets, and blockquotes at `72` columns (preserving `**Bold**` headings and fenced code blocks), formats `Verified:` + `Agent:` trailers, and validates before committing:

```bash
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  code_quality_and_testing \
  "fix(test): align Android TV Robolectric test to SDK 36" \
  --body-file - \
  --verified "68/68 tests (--rerun-tasks), lintDebug, assembleDebug" <<'EOF'
- **Robolectric**: Standardize `@Config(sdk = [36], qualifiers = "television")`
  in `TvAndHeaderLayoutE2ETest.kt` to prevent dual-SDK
  `ZipFileSystemProvider` collisions.
EOF
```

Short inline usage (also supported):

```bash
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  discovery_and_grouping \
  "docs(discovery): clarify 3-tier UDN fallback on DHCP renewal" \
  '- Clarify `<serialNumber>` to `<UDN>` fallback behavior during DHCP lease renewal in `DISCOVERY_AND_ROOM_GROUPING.md`.' \
  --verified "SkuAndCryptoTest & SoapAndSelfHealingTest passing"
```

Supported flags:
- `-v, --verified "<text>"`: adds a `Verified: <text>` trailer above `Agent:` (or pass as 4th positional argument).
- `-F, --body-file <path|->`: reads multiline Markdown body from a file or stdin (`-`).
- `--amend`: amends `HEAD` (`git commit --amend`).
- `--dry-run`: prints the formatted, validated message without committing.

#### Option B — Direct `git commit` with Heredoc
```bash
git commit -F - <<'EOF'
docs(discovery): clarify 3-tier UDN fallback on DHCP renewal

- Clarify `<serialNumber>` to `<UDN>` fallback behavior during DHCP
  lease renewal in `DISCOVERY_AND_ROOM_GROUPING.md`.

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
2. **Commit description field** (Git-safe Markdown bullets + `Verified:` + `Agent:` trailer):
   ```markdown
   - **Touch Targets**: Adjust top-first dimmer slider padding in
     `DeviceRow.kt` to preserve `48.dp` targets on phone and tablet
     layouts.

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
   - Does the body use clean Git-safe Markdown (`- ` bullets, `` `code` ``, `**Bold**` labels — no `#` headings) wrapped at `<= 72` chars?
   - Does the bottom trailer block include `Agent: <Canonical Agent Name> (<role_id>)` (and `Verified:` when applicable)?
2. Validate `HEAD` directly:
   ```bash
   bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh <(git log -1 --pretty=%B)
   ```
3. Query commits by agent using native Git trailer formatting:
   ```bash
   git log -n 10 --format="%h %s | %(trailers:key=Agent,valueonly)"
   ```
