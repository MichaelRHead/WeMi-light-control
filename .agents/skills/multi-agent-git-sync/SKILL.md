---
name: multi-agent-git-sync
description: Multi-agent Git synchronization, anti-stomp audit, conflict resolution, and coherent integration workflow for concurrent agent conversations and AI Studio code syncs. Use before starting work, before pushing commits, when a git push is rejected, or when asked to sync up and review changes from other agents.
---

# Multi-Agent Git Sync & Conflict-Free Integration Skill (`multi-agent-git-sync`)

Use this skill whenever multiple agent conversations or external tools (such as Google AI Studio and parallel implementation/planning agents) are actively committing to the same repository (including `MichaelRHead/WeMi-light-control`).

---

## 1. Core Principles for Multi-Agent Collaboration

1. **Never Stomp on Another Agent's Work**:
   - Never use `git push --force` or blindly accept `--ours` / `--theirs` during a rebase or merge.
   - Treat every incoming commit on `origin/main` as intentional work from a peer agent or the user that must be understood, preserved, and integrated with your own changes.
2. **Audit for Accidental Overwrites ("Anti-Stomp Check")**:
   - When two agents edit the same file around the same time, a later commit that rewrites or restructures a file (e.g., reframing `IMPLEMENTED.md`, renaming `docs/IMPLEMENTATION_PLAN.md` to `UPCOMING_FEATURES.md`, or refactoring a Compose dialog) can inadvertently drop lines added by an earlier commit.
   - Always diff your recent commits against the latest `origin/main` to verify that none of your features, test tags, documentation sections, or links were lost.
3. **Keep Code, Tests, User Guides, Change Log, and Skills Coherent**:
   - After integrating incoming commits, verify that version numbers (`versionCode` / `versionName` in `app/build.gradle.kts`), file line counts, `Modifier.testTag(...)` identifiers, `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `USER_GUIDE.md`, `AGENTS.md`, and `.agents/skills/*/SKILL.md` all agree with each other.
4. **Respect Exclusive Ownership of `UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md` & `wemi-ux-design-and-experience`**:
   - [`UX_DESIGN.md`](../../UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](../../UX_DESIGN_AND_EXPERIENCE.md), and [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](../wemi-ux-design-and-experience/SKILL.md) are **owned exclusively by the Google AI Studio Agent**.
   - Other agents must read and follow those files to understand the app's UX guidelines, navigation patterns, accessibility standards, behavioral modeling, layout invariants, and CUJs, **but must never edit, overwrite, or reformat them**.
5. **Single-Source Documentation Layout (`ALL_CAPS.md` at Root Only, User Guides in `docs/user-guide/`)**:
   - All `ALL_CAPS.md` files (`AGENTS.md`, `UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md`, `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `USER_GUIDE.md`, `CODE_QUALITY_AND_MODULARIZATION_PLAN.md`) live **exclusively at the repository root (`/`)**.
   - The `docs/` directory is dedicated **only** to the human-focused user guides (`docs/user-guide/*.md` and `docs/README.md`). Never duplicate or copy `ALL_CAPS.md` files into `docs/`.

---

## 2. Step-by-Step Sync, Review, Reapply & Submit Workflow

### Step 1: Fetch & Inspect Incoming Commits Before Touching Files
Run a non-destructive fetch and inspect the commit graph and diffs between your local `HEAD` and `origin/main`:
```bash
git fetch --all
git status
git log --graph --oneline --decorate -n 15 HEAD origin/main
```
If `origin/main` has new commits (`HEAD..origin/main` is non-empty), inspect every incoming commit in detail:
```bash
git log -p --stat HEAD..origin/main
```

### Step 2: Run the Anti-Stomp Audit on Your Previous Commits
Identify the files you created or modified in your recent commits and check whether incoming commits touched or renamed any of those same files:
```bash
# List files modified in your recent commit(s) vs incoming commits
git diff --name-status <your-earliest-commit>..origin/main
```
Check specifically for:
1. **Dropped Additions in Shared Files**:
   - If an incoming commit rewrote a file you also edited (`IMPLEMENTED.md`, `AGENTS.md`, `USER_GUIDE.md`, `MainActivity.kt`, `WeMoDialogs.kt`, etc.), inspect `git diff <your-commit>..origin/main -- <file>`.
   - If any of your additions (such as a new section, test tag, bug fix, or skill link) were accidentally overwritten, **reapply them cleanly** into the new structure of the file.
2. **File Renames or Deletions**:
   - If an incoming commit renamed or deleted a file (for example, replacing `docs/IMPLEMENTATION_PLAN.md` with `UPCOMING_FEATURES.md`), search the entire workspace for stale references:
     ```bash
     grep -rn "<old-file-name>" . .agents/
     ```
   - Update every link in your plans, docs, and skills to point to the new file path.
3. **Version & Metric Drift**:
   - Check `app/build.gradle.kts` (`versionCode` / `versionName`) and `wc -l $(find app/src -name "*.kt" | sort)`.
   - If another agent bumped the version (e.g., `v11.0` → `v12.0`) or added UI/network code, update your documentation and plans to reflect the new version and any new components or `testTag`s.

### Step 3: Rebase & Resolve Conflicts Additively
Integrate incoming commits using rebase so history stays linear and readable:
```bash
git pull --rebase origin main
```
If Git reports a conflict in any file:
1. Open the conflicting file and inspect both `<<<<<<< HEAD` (incoming `origin/main`) and `>>>>>>> <commit>` (your change).
2. **Merge both sides additively**:
   - In `IMPLEMENTED.md`: keep both reverse-chronological table rows and both detailed sections ordered newest-first by timestamp.
   - In `AGENTS.md` / `SKILL.md` / `USER_GUIDE.md`: keep all document and skill links from both commits.
   - In Kotlin source/test files: preserve both agents' methods, parameters, imports, and `Modifier.testTag(...)` attributes.
3. Stage the resolved files (`git add <file>`) and run `git rebase --continue`.

### Step 4: Verify Documentation Organization & Skill Synchronization
Before committing or pushing your integrated changes:
1. **Verify Single-Source Root `ALL_CAPS.md` Layout**:
   - Confirm that all `ALL_CAPS.md` files (`AGENTS.md`, `UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md`, `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `USER_GUIDE.md`, `CODE_QUALITY_AND_MODULARIZATION_PLAN.md`) exist only at the repository root (`/`) and are **not** duplicated inside `docs/`.
   - Do not modify `UX_DESIGN.md` or `UX_DESIGN_AND_EXPERIENCE.md` unless you are the Google AI Studio Agent.
2. **Sync Repository Skills (`.agents/skills/`) with Persistent Agent Skills**:
   - Whenever any `.agents/skills/<skill-name>/SKILL.md` is added or updated in the repository, also call `save_skill(overwrite = true, skill_dir = "/home/antigravity/MichaelRHead/WeMi-light-control/.agents/skills/<skill-name>")` so the persistent skill registry stays in lockstep with GitHub.

### Step 5: Mandatory Pre-Push Verification & Atomic Push
1. If any Kotlin source, resource, or test files were modified, run the mandatory build & test gate:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```
2. Stage, commit, and push atomically, re-checking `git pull --rebase origin main` if another agent pushed in the meantime:
   ```bash
   git add -A
   git commit -m "<concise commit message>"
   git pull --rebase origin main && git push origin main
   ```
