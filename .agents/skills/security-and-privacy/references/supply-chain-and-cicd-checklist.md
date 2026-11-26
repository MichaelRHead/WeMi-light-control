# Supply Chain, CI/CD & Build Script Security Checklist

Reference guide for the `security-and-privacy` skill. Covers GitHub Actions, build systems (Gradle/Maven/npm/Python), release scripts, and repository hygiene.

---

## 1. GitHub Actions & CI/CD Hardening
- **Least-Privilege `GITHUB_TOKEN`**: Every workflow should declare an explicit top-level `permissions: {}` (or `contents: read`) and grant elevated permissions (`contents: write`, `id-token: write`, `packages: write`) only at the specific job level that requires them.
- **Action Pinning**: Pin third-party GitHub Actions to full 40-character immutable commit SHAs (`uses: owner/action@<40-hex-sha> # vX.Y.Z`) rather than mutable version tags (`@v4`, `@main`) to defend against tag-retargeting supply-chain attacks. First-party `actions/*` should ideally also be pinned by SHA in release/publishing workflows holding signing keys or Play Store credentials.
- **Expression / Script Injection**: Never interpolate untrusted `${{ github.event.* }}` values (PR title/body, branch name, commit message, issue title, workflow_dispatch inputs) directly inside `run:` shell commands. Always bind them via job/step `env:` variables and quote `"$VAR"` properly in shell scripts.
- **Untrusted Triggers (`pull_request_target`, `workflow_run`)**: Never check out and execute untrusted fork code in a workflow holding repository write permissions or deployment secrets.
- **Secrets & Credential Lifecycle**:
  - Avoid writing secrets (Play Store service-account JSON, keystores, API tokens) to world-readable `/tmp` files or leaving them on disk after steps complete; use `mktemp` with `chmod 600` (or `umask 077`) and `trap 'rm -f ...' EXIT` or `if: always()` cleanup steps.
  - Never echo secrets, base64-decoded keys, or auth headers to stdout/stderr or upload them in workflow artifacts.
  - Scope production signing / store credentials to protected GitHub **Environments** with branch protection rules where applicable.
- **Concurrency & Replay Safety**: Ensure release/promotion workflows use `concurrency:` groups and validate git refs/commits cleanly so stale runs cannot overwrite newer releases.

## 2. Build System & Dependency Integrity
- **Repository Hygiene**: Verify repositories use HTTPS (`https://`), prefer explicit `google()` / `mavenCentral()` with repository content filtering where feasible, and never disable TLS verification (`-k`, `--insecure`, `TrustAllCerts`) in build/setup scripts.
- **Dependency Minimalism & Lockfiles**: Check for unnecessary transitive libraries, snapshot/dynamic versions (`+`, `latest.release`), and lockfile/checksum verification (`gradle/verification-metadata.xml`, `package-lock.json`, `requirements.txt --require-hashes`).
- **Build & Dev-Env Scripts**:
  - Audit shell and Python scripts for command injection (unquoted variables, `eval`, `shell=True`, unsafe `os.system`), unsafe `curl | bash`, predictable `/tmp/...` paths (TOCTOU / symlink attacks), and hardcoded tokens or paths.
  - Check custom TLS/truststore or proxy hooks in setup scripts to ensure they never weaken certificate verification globally or leak credentials into shell profiles.
  - Audit release/publishing scripts (e.g., Google Play API upload/promote scripts) for strict input validation, safe file handling, non-leaky error logs, and no accidental credential persistence.

## 3. Repository & Artifact Hygiene
- **Secret Scanning**: Check tracked files, example configs, test fixtures, and scripts for accidentally committed private keys, API keys, service account JSON, Wi-Fi credentials, or real personal MAC/IP/location data.
- **Debug & Test Backdoors**: Ensure test-only endpoints, mock auth bypasses, loopback overrides, or verbose diagnostic flags cannot be triggered by external attackers in production builds.
- **Binary & Metadata Transparency**: Check APK/AAB packaging flags (`dependenciesInfo`, native `.so` inclusions, R8/ProGuard stripping of debug logs and assertions, `android:debuggable`, `android:testOnly`).
