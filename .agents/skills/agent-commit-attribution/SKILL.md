---
name: agent-commit-attribution
description: Mandatory Git commit message and commit description attribution convention for all agents working on WeMi Light Control. Use before every git commit, git commit --amend, rebase commit, automated release script commit, or AI Studio GitHub export so every commit description explicitly identifies the authoring agent's canonical name and role ID.
---

# Agent Commit Attribution Skill (`agent-commit-attribution`)

Use this skill **before creating any Git commit** (via `git commit`, helper scripts, or Google AI Studio's GitHub export UI) in `MichaelRHead/WeMi-light-control` or any multi-agent repository.

---

## 1. Why Every Commit Description Must Name the Agent

All five collaborating agents push commits using the repository owner's GitHub credentials (`Author: Michael R. Head`). Without explicit agent attribution in the commit message and commit description body, `git log` and GitHub's commit history cannot distinguish which specialist agent authored a change, restored script permissions, refactored a module, or signed off a release stage.

**Mandatory Invariant**:
1. **Never create bare one-liner commits** (`git commit -m "..."` without a description body).
2. **Always include the authoring agent's short name in the commit subject** (`[<Short Agent Name>]`) **AND the full canonical agent name + role ID in the commit description body** (`Agent: <Canonical Agent Name> (<role_id>)`).

---

## 2. Canonical Agent Names & Role IDs

Every agent must identify itself using its canonical name and role ID from the table below:

| Stage / Domain | Role ID | Canonical Agent Name (`Agent:` Trailer) | Short Subject Tag |
| :--- | :--- | :--- | :--- |
| **UX, Design, Assets & Release Init/Conclude** | `ai_studio_ux_cuj` | `Google AI Studio Agent` | `[Google AI Studio Agent]` |
| **SSDP/Unicast Discovery, Identity & Room Grouping** | `discovery_and_grouping` | `Antigravity Device Discovery & Room Grouping Agent` | `[Discovery & Room Grouping Agent]` |
| **10.22.22.1 Setup Telemetry, SOAP Logs & Support** | `telemetry_and_debugging` | `Antigravity Telemetry & Debugging Agent` | `[Telemetry & Debugging Agent]` |
| **Modularity, Build Hygiene & Hermetic/E2E Testing** | `code_quality_and_testing` | `Antigravity Code Quality & E2E Testing Agent` | `[Code Quality & E2E Testing Agent]` |
| **MIT License Parity, Dependencies & Brand Hygiene** | `license_governance` | `Antigravity License & Brand Governance Agent` | `[License & Brand Governance Agent]` |
| *(Unassigned / General Task Agent)* | `antigravity_general` | `Antigravity General Agent` *(or specific sub-role)* | `[Antigravity General Agent]` |

---

## 3. Required Commit Message & Commit Description Format

Every commit must follow this exact structure (subject line <= 100 chars when practical, blank line, 1–4 sentence commit description, blank line, and `Agent:` attribution line):

```text
<type>(<scope>): <concise imperative summary> [<Short Agent Name>]

<1–4 sentence commit description explaining what changed, why, and how it was verified.>

Agent: <Canonical Agent Name> (<role_id>)
```

Allowed `<type>` prefixes: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `style`.

---

## 4. How to Commit in Each Environment

### 4.1 Antigravity Agents (Linux VM CLI)

#### Option A — Recommended Wrapper Script (`commit_as_agent.sh`)
Stage your files (`git add ...`) and invoke [`scripts/commit_as_agent.sh`](scripts/commit_as_agent.sh), which automatically appends `[<Short Agent Name>]`, formats the commit description body, appends `Agent: <Canonical Agent Name> (<role_id>)`, and validates the commit message:

```bash
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  code_quality_and_testing \
  "fix(test): align Android TV Robolectric test to SDK 36" \
  "Standardize @Config(sdk = [36], qualifiers = \"television\") in ExampleRobolectricTest to prevent dual-SDK ZipFileSystemProvider collisions; verified 30/30 unit and Compose E2E tests passing."
```

#### Option B — Direct Multi-Paragraph `git commit`
If running `git commit` directly, always pass at least three `-m` paragraphs (or a heredoc via `git commit -F - <<'EOF'`):

```bash
git commit \
  -m "docs(discovery): update 3-tier UDN fallback examples [Discovery & Room Grouping Agent]" \
  -m "Clarify <serialNumber> to <UDN> fallback behavior during DHCP lease renewal in DISCOVERY_AND_ROOM_GROUPING.md; verified SkuAndCryptoTest and SoapAndSelfHealingTest passing." \
  -m "Agent: Antigravity Device Discovery & Room Grouping Agent (discovery_and_grouping)"
```

#### Option C — Install the Local `commit-msg` Git Hook on VM Startup
Run this once in your VM workspace so Git automatically blocks any commit missing a description body or `Agent:` attribution:

```bash
bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh --install-hook
```

### 4.2 Google AI Studio Agent (GitHub Export / Sync UI)

When exporting or committing changes to GitHub from Google AI Studio:
1. **Commit summary / message line**: End the summary with `[Google AI Studio Agent]`:
   ```text
   feat(ui): refine dimmer brightness slider spacing [Google AI Studio Agent]
   ```
2. **Commit description field**: Always populate the commit description with 1–3 sentences describing the UX, Compose, or Play Store asset changes, followed by the `Agent:` trailer:
   ```text
   Adjusted top-first dimmer slider padding in DeviceRow.kt to preserve 48dp touch targets and verified all 30 unit and Compose E2E tests pass.

   Agent: Google AI Studio Agent (ai_studio_ux_cuj)
   ```

### 4.3 Automated Release State-Machine Commits (`scripts/advance_release_turn.sh`)

[`scripts/advance_release_turn.sh`](../../../scripts/advance_release_turn.sh) automatically maps `initiate`, `sign_off <role>`, `conclude`, and `request_rereview <from_role> ...` to the acting agent's canonical name and generates a compliant commit subject and commit description body containing `Agent: <Canonical Agent Name> (<role_id>)`.

---

## 5. Pre-Commit Attribution Checklist

Before running `git push origin main`:
1. Inspect your outgoing commit(s) with `git log -n 2 --format=fuller`:
   - Does the subject line clearly state `<type>(<scope>): ... [<Short Agent Name>]`?
   - Is there a non-empty commit description body explaining what changed and why?
   - Does the commit description end with `Agent: <Canonical Agent Name> (<role_id>)`?
2. If any local unpushed commit is missing attribution, amend it (`git commit --amend`) or validate it with:
   ```bash
   bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh <(git log -1 --pretty=%B)
   ```
