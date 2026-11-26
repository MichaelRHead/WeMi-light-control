#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Fast, Reuse-First Cloud & Developer Environment Setup
# ==============================================================================
# Configures JDK 21, Android SDK 36/36.1 (build-tools 36.0.0), optional shared
# warm caches, and dynamic JVM/Gradle proxy & TLS CA syncing completely in user
# space without root/sudo.
#
# Performance Architecture:
#   - Reuses preinstalled system JDK 21 (e.g. /usr/lib/jvm/java-21-openjdk-*)
#     and writable preinstalled Android SDKs (e.g. /opt/android-sdk) whenever
#     available, deferring missing platform/build-tools deltas to AGP's built-in
#     on-demand SDK auto-download (~0.1-0.2s VM startup instead of ~14-44s).
#   - Ensures Android SDK licenses are accepted before returning so Gradle/AGP
#     can download platforms;android-36.1 and build-tools;36.0.0 seamlessly on
#     the first build. Pass --install-sdk to eagerly pre-install SDK packages.
#   - Skips redundant keytool truststore rebuilds when system Java cacerts
#     (/etc/ssl/certs/java/cacerts) already includes all local CA certificates.
#   - On environments with persistent $HOME/.gemini/caches/wemi-android-v1/,
#     restores immutable lz4/gzip archives in parallel onto fast local disk.
#   - Falls back cleanly to direct network downloads on a cache miss or bare VM.
#   - Pass --save-caches after a warm build/test run to refresh shared caches
#     (automatically implies --install-sdk).
#
# Idempotent and non-interactive: safe to run repeatedly on fresh cloud VMs and
# across VM sleep/wake cycles.
# ==============================================================================
set -euo pipefail

SAVE_CACHES=0
INSTALL_SDK=0
for arg in "$@"; do
  case "$arg" in
    --install-sdk) INSTALL_SDK=1 ;;
    --save-caches)
      SAVE_CACHES=1
      INSTALL_SDK=1
      ;;
    -h|--help)
      echo "Usage: $0 [--install-sdk] [--save-caches]"
      exit 0
      ;;
    *)
      echo "[env-setup] Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DEFAULT_LOCAL_JDK="$HOME/.local/jdk-21"
DEFAULT_LOCAL_SDK="$HOME/android-sdk"
LOCAL_BIN_DIR="$HOME/.local/bin"
GRADLE_DIR="$HOME/.gradle"
M2_DIR="$HOME/.m2"
SYNC_HELPER="$LOCAL_BIN_DIR/wemi-sync-java-proxy.sh"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

mkdir -p "$LOCAL_BIN_DIR" "$HOME/.local" "$GRADLE_DIR" "$M2_DIR"

# Detect CPU architecture
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64) ADOPTIUM_ARCH="x64" ;;
  aarch64|arm64) ADOPTIUM_ARCH="aarch64" ;;
  *)
    echo "[env-setup] Unsupported CPU architecture: $ARCH" >&2
    exit 1
    ;;
esac

# Detect fast persistent shared cache tier ($HOME/.gemini/caches) & compressor
CACHE_DIR=""
if [ -n "${WEMI_CACHE_DIR:-}" ]; then
  CACHE_DIR="$WEMI_CACHE_DIR"
elif [ -d "$HOME/.gemini" ] && [ -w "$HOME/.gemini" ]; then
  CACHE_DIR="$HOME/.gemini/caches/wemi-android-v1"
fi
if [ -n "$CACHE_DIR" ]; then
  mkdir -p "$CACHE_DIR" 2>/dev/null || CACHE_DIR=""
fi

if command -v lz4 >/dev/null 2>&1; then
  ARCHIVE_EXT="tar.lz4"
  compress_stream() { lz4 -1 -q; }
  decompress_stream() { lz4 -dc "$1"; }
elif command -v pigz >/dev/null 2>&1; then
  ARCHIVE_EXT="tar.gz"
  compress_stream() { pigz -1; }
  decompress_stream() { pigz -dc "$1"; }
else
  ARCHIVE_EXT="tar.gz"
  compress_stream() { gzip -1; }
  decompress_stream() { gzip -dc "$1"; }
fi

# Compute Gradle dependency cache key (ignores routine versionCode/versionName bumps)
compute_deps_key() {
  {
    for f in \
      "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" \
      "$REPO_ROOT/gradle/libs.versions.toml" \
      "$REPO_ROOT/settings.gradle.kts" \
      "$REPO_ROOT/build.gradle.kts"; do
      [ -f "$f" ] && cat "$f"
    done
    if [ -f "$REPO_ROOT/app/build.gradle.kts" ]; then
      grep -vE '^\s*(versionCode|versionName)\s*=' "$REPO_ROOT/app/build.gradle.kts" || true
    fi
  } | sha256sum | awk '{print substr($1, 1, 12)}'
}

DEPS_KEY="$(compute_deps_key)"
JDK_CACHE="${CACHE_DIR:+${CACHE_DIR}/jdk-21-${ADOPTIUM_ARCH}.${ARCHIVE_EXT}}"
SDK_CACHE="${CACHE_DIR:+${CACHE_DIR}/android-sdk-36.1-${ADOPTIUM_ARCH}.${ARCHIVE_EXT}}"
M2_CACHE="${CACHE_DIR:+${CACHE_DIR}/robolectric-m2-sdk36.${ARCHIVE_EXT}}"
GRADLE_CACHE="${CACHE_DIR:+${CACHE_DIR}/gradle-warm-cache-${DEPS_KEY}.${ARCHIVE_EXT}}"

save_archive_atomic() {
  local dest="$1"
  shift
  [ -n "$dest" ] || return 0
  local tmp="${dest}.tmp.$$"
  if tar "$@" | compress_stream > "$tmp"; then
    mv -f "$tmp" "$dest"
  else
    rm -f "$tmp"
    return 1
  fi
}

is_jdk21() {
  local dir="$1"
  [ -n "$dir" ] && [ -x "$dir/bin/java" ] && [ -x "$dir/bin/javac" ] || return 1
  if [ -f "$dir/release" ] && grep -qE '^JAVA_VERSION="21(\.|")' "$dir/release"; then
    return 0
  fi
  "$dir/bin/java" -version 2>&1 | head -n 1 | grep -qE '"21(\.|")'
}

has_required_sdk() {
  local dir="$1"
  [ -n "$dir" ] &&
  [ -x "$dir/cmdline-tools/latest/bin/sdkmanager" ] &&
  [ -x "$dir/platform-tools/adb" ] &&
  [ -f "$dir/platforms/android-36/android.jar" ] &&
  [ -f "$dir/platforms/android-36.1/android.jar" ] &&
  [ -x "$dir/build-tools/36.0.0/aapt2" ]
}

is_writable_base_sdk() {
  local dir="$1"
  [ -n "$dir" ] && [ -d "$dir" ] && [ -w "$dir" ] &&
  [ -x "$dir/cmdline-tools/latest/bin/sdkmanager" ] &&
  { [ ! -d "$dir/platforms" ] || [ -w "$dir/platforms" ]; } &&
  { [ ! -d "$dir/build-tools" ] || [ -w "$dir/build-tools" ]; } &&
  { [ ! -d "$dir/licenses" ] || [ -w "$dir/licenses" ]; }
}

# Resolve JDK 21: prefer preinstalled system JDK 21 (inherits system cacerts),
# then local user JDK 21, then cache restore / Adoptium download fallback.
JAVA_HOME_DIR=""
for cand in \
  "${WEMI_JAVA_HOME:-}" \
  "/usr/lib/jvm/java-21-openjdk-amd64" \
  "/usr/lib/jvm/java-21-openjdk-arm64" \
  "/usr/lib/jvm/java-21-openjdk" \
  "$DEFAULT_LOCAL_JDK" \
  "${JAVA_HOME:-}"; do
  if is_jdk21 "$cand"; then
    JAVA_HOME_DIR="$cand"
    break
  fi
done
if [ -z "$JAVA_HOME_DIR" ]; then
  JAVA_HOME_DIR="$DEFAULT_LOCAL_JDK"
fi

# Resolve Android SDK:
# 1. Explicit WEMI_ANDROID_SDK_DIR override
# 2. Any already-complete SDK (instant idempotent rerun)
# 3. Persistent shared SDK cache archive (if present) -> $HOME/android-sdk
# 4. Writable preinstalled SDK with cmdline-tools (e.g. /opt/android-sdk)
# 5. Fallback to user-space $HOME/android-sdk
ANDROID_SDK_DIR=""
if [ -n "${WEMI_ANDROID_SDK_DIR:-}" ]; then
  ANDROID_SDK_DIR="$WEMI_ANDROID_SDK_DIR"
else
  for cand in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "/opt/android-sdk" "$DEFAULT_LOCAL_SDK"; do
    if has_required_sdk "$cand"; then
      ANDROID_SDK_DIR="$cand"
      break
    fi
  done
  if [ -z "$ANDROID_SDK_DIR" ] && [ -n "$SDK_CACHE" ] && [ -f "$SDK_CACHE" ]; then
    ANDROID_SDK_DIR="$DEFAULT_LOCAL_SDK"
  fi
  if [ -z "$ANDROID_SDK_DIR" ]; then
    for cand in "/opt/android-sdk" "/usr/local/lib/android/sdk" "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$DEFAULT_LOCAL_SDK"; do
      if is_writable_base_sdk "$cand"; then
        ANDROID_SDK_DIR="$cand"
        break
      fi
    done
  fi
  if [ -z "$ANDROID_SDK_DIR" ]; then
    ANDROID_SDK_DIR="$DEFAULT_LOCAL_SDK"
  fi
fi
mkdir -p "$ANDROID_SDK_DIR/cmdline-tools"

# 1. Fast parallel restore from persistent shared cache onto local fast disk
RESTORE_PIDS=()
RESTORE_NAMES=()

if [ -n "$CACHE_DIR" ]; then
  if [ "$JAVA_HOME_DIR" = "$DEFAULT_LOCAL_JDK" ] && ! is_jdk21 "$JAVA_HOME_DIR" && [ -f "$JDK_CACHE" ]; then
    (
      tmp_parent="$HOME/.local/.jdk-restore.$$"
      rm -rf "$tmp_parent" && mkdir -p "$tmp_parent"
      decompress_stream "$JDK_CACHE" | tar -xf - -C "$tmp_parent"
      rm -rf "$JAVA_HOME_DIR"
      mv "$tmp_parent/jdk-21" "$JAVA_HOME_DIR"
      rm -rf "$tmp_parent"
    ) &
    RESTORE_PIDS+=("$!")
    RESTORE_NAMES+=("jdk-21")
  fi

  if [ "$ANDROID_SDK_DIR" = "$DEFAULT_LOCAL_SDK" ] && ! has_required_sdk "$ANDROID_SDK_DIR" && [ -f "$SDK_CACHE" ]; then
    (
      tmp_parent="$HOME/.android-sdk-restore.$$"
      rm -rf "$tmp_parent" && mkdir -p "$tmp_parent"
      decompress_stream "$SDK_CACHE" | tar -xf - -C "$tmp_parent"
      rm -rf "$ANDROID_SDK_DIR"
      mv "$tmp_parent/android-sdk" "$ANDROID_SDK_DIR"
      rm -rf "$tmp_parent"
    ) &
    RESTORE_PIDS+=("$!")
    RESTORE_NAMES+=("android-sdk")
  fi

  if [ ! -f "$M2_DIR/.wemi-robolectric-seeded" ] && \
     [ ! -d "$M2_DIR/repository/org/robolectric/android-all-instrumented" ] && \
     [ -f "$M2_CACHE" ]; then
    (
      decompress_stream "$M2_CACHE" | tar -xf - -C "$M2_DIR"
      touch "$M2_DIR/.wemi-robolectric-seeded"
    ) &
    RESTORE_PIDS+=("$!")
    RESTORE_NAMES+=("robolectric-m2")
  fi

  if [ ! -f "$GRADLE_DIR/.wemi-cache-seeded" ] && \
     [ ! -d "$GRADLE_DIR/caches/modules-2/files-2.1" ]; then
    GRADLE_RESTORE_SRC=""
    if [ -f "$GRADLE_CACHE" ]; then
      GRADLE_RESTORE_SRC="$GRADLE_CACHE"
    else
      # Fall back to the newest prior dependency cache so upgraded deps only download deltas
      GRADLE_RESTORE_SRC="$(ls -t "$CACHE_DIR"/gradle-warm-cache-*."$ARCHIVE_EXT" 2>/dev/null | head -n 1 || true)"
    fi
    if [ -n "$GRADLE_RESTORE_SRC" ] && [ -f "$GRADLE_RESTORE_SRC" ]; then
      (
        decompress_stream "$GRADLE_RESTORE_SRC" | tar -xf - -C "$GRADLE_DIR"
        touch "$GRADLE_DIR/.wemi-cache-seeded"
      ) &
      RESTORE_PIDS+=("$!")
      RESTORE_NAMES+=("gradle-cache")
    fi
  fi

  if [ "${#RESTORE_PIDS[@]}" -gt 0 ]; then
    echo "[env-setup] Restoring cached toolchains/dependencies in parallel: ${RESTORE_NAMES[*]}..."
    for i in "${!RESTORE_PIDS[@]}"; do
      if ! wait "${RESTORE_PIDS[$i]}"; then
        echo "[env-setup] Warning: cache restore failed for ${RESTORE_NAMES[$i]}; falling back to network." >&2
      fi
    done
  fi
fi

# 2. Network fallback: Install pinned Temurin JDK 21 GA with SHA-256 verification if no JDK 21 is available
if ! is_jdk21 "$JAVA_HOME_DIR"; then
  JAVA_HOME_DIR="$DEFAULT_LOCAL_JDK"
  case "$ADOPTIUM_ARCH" in
    x64)
      TEMURIN_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz"
      TEMURIN_SHA256="bb8a93f9ae6e7a693b2b721e85dbeff26ae1a77447985a8d7afcdd9c7cf6222e"
      ;;
    aarch64)
      TEMURIN_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13.1_2.tar.gz"
      TEMURIN_SHA256="8c72816a6b308842d5ed1f13e5826b69c567da4784f7238d7e800efa71fe54b1"
      ;;
  esac
  echo "[env-setup] Downloading pinned Temurin JDK 21 (${ADOPTIUM_ARCH}) into $JAVA_HOME_DIR..."
  TMP_JDK="$(mktemp /tmp/temurin-jdk21-XXXXXX.tar.gz)"
  curl -fSL "$TEMURIN_URL" -o "$TMP_JDK"
  if ! printf '%s  %s\n' "$TEMURIN_SHA256" "$TMP_JDK" | sha256sum -c - >/dev/null; then
    echo "[env-setup] ERROR: SHA-256 mismatch for Temurin JDK 21 archive!" >&2
    rm -f "$TMP_JDK"
    exit 1
  fi
  rm -rf "${JAVA_HOME_DIR}.tmp"
  mkdir -p "${JAVA_HOME_DIR}.tmp"
  tar -xzf "$TMP_JDK" -C "${JAVA_HOME_DIR}.tmp" --strip-components=1
  rm -f "$TMP_JDK"
  rm -rf "$JAVA_HOME_DIR"
  mv "${JAVA_HOME_DIR}.tmp" "$JAVA_HOME_DIR"
fi

if [ -w "$JAVA_HOME_DIR/lib/security" ] && [ -f "$JAVA_HOME_DIR/lib/security/cacerts" ] && [ ! -f "$JAVA_HOME_DIR/lib/security/cacerts.orig" ]; then
  cp "$JAVA_HOME_DIR/lib/security/cacerts" "$JAVA_HOME_DIR/lib/security/cacerts.orig"
fi

# 3. Network fallback: Install Android SDK cmdline-tools with SHA-256 verification if still missing
CMDLINE_TOOLS_SHA256="7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee"
if [ ! -x "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[env-setup] Downloading Android SDK command-line tools into $ANDROID_SDK_DIR..."
  TMP_CLI="$(mktemp /tmp/android-cmdline-tools-XXXXXX.zip)"
  curl -fSL "$CMDLINE_TOOLS_URL" -o "$TMP_CLI"
  if ! printf '%s  %s\n' "$CMDLINE_TOOLS_SHA256" "$TMP_CLI" | sha256sum -c - >/dev/null; then
    echo "[env-setup] ERROR: SHA-256 mismatch for Android cmdline-tools archive!" >&2
    rm -f "$TMP_CLI"
    exit 1
  fi
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  mkdir -p "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  unzip -q "$TMP_CLI" -d "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  rm -f "$TMP_CLI"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest"
  mv "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
fi

# 4. Install/update fast (<15ms) dynamic proxy & TLS CA sync helper
TMP_SYNC="${SYNC_HELPER}.tmp.$$"
cat <<'EOF' > "$TMP_SYNC"
#!/usr/bin/env bash
# Synchronize JDK/Gradle proxy settings and custom CA truststore only when needed.
set -euo pipefail

JAVA_HOME_DIR="${JAVA_HOME:-$HOME/.local/jdk-21}"
GRADLE_DIR="$HOME/.gradle"
TRUSTSTORE="$GRADLE_DIR/proxy-cacerts.jks"
CA_FP_FILE="$GRADLE_DIR/proxy-ca.fingerprint"
PROXY_FP_FILE="$GRADLE_DIR/proxy-env.fingerprint"
GRADLE_PROPS="$GRADLE_DIR/gradle.properties"
PROXY_ENV_FILE="$GRADLE_DIR/proxy.env"
SSL_BUNDLE="${SSL_CERT_FILE:-/etc/ssl/certs/ca-certificates.crt}"
SYSTEM_JAVA_CACERTS="/etc/ssl/certs/java/cacerts"

mkdir -p "$GRADLE_DIR"

# 1. Check whether the active JDK already uses an up-to-date system Java cacerts
JDK_USES_SYSTEM_CACERTS=0
USE_SYSTEM_CACERTS=0
if [ -f "$SYSTEM_JAVA_CACERTS" ] && [ -f "$JAVA_HOME_DIR/lib/security/cacerts" ] && \
   [ "$(readlink -f "$JAVA_HOME_DIR/lib/security/cacerts" 2>/dev/null || true)" = "$(readlink -f "$SYSTEM_JAVA_CACERTS" 2>/dev/null || true)" ] && \
   [ "$SSL_BUNDLE" = "/etc/ssl/certs/ca-certificates.crt" ]; then
  JDK_USES_SYSTEM_CACERTS=1
  USE_SYSTEM_CACERTS=1
  if [ -f "$SSL_BUNDLE" ] && [ "$SSL_BUNDLE" -nt "$SYSTEM_JAVA_CACERTS" ]; then
    USE_SYSTEM_CACERTS=0
  fi
  if [ "$USE_SYSTEM_CACERTS" -eq 1 ]; then
    for cert in /usr/local/share/ca-certificates/*.crt; do
      [ -f "$cert" ] || continue
      if [ "$cert" -nt "$SYSTEM_JAVA_CACERTS" ]; then
        USE_SYSTEM_CACERTS=0
        break
      fi
    done
  fi
fi

ACTIVE_TRUSTSTORE=""
if [ "$USE_SYSTEM_CACERTS" -eq 0 ] && [ -x "$JAVA_HOME_DIR/bin/keytool" ] && [ -f "$JAVA_HOME_DIR/lib/security/cacerts" ]; then
  BASE_CACERTS="$JAVA_HOME_DIR/lib/security/cacerts"
  if [ -w "$JAVA_HOME_DIR/lib/security" ]; then
    if [ ! -f "$JAVA_HOME_DIR/lib/security/cacerts.orig" ]; then
      cp "$JAVA_HOME_DIR/lib/security/cacerts" "$JAVA_HOME_DIR/lib/security/cacerts.orig"
    fi
    BASE_CACERTS="$JAVA_HOME_DIR/lib/security/cacerts.orig"
  fi

  EXTRA_CA_FILES=()
  for cert in /usr/local/share/ca-certificates/*.crt; do
    [ -f "$cert" ] && EXTRA_CA_FILES+=("$cert")
  done
  CA_FILES=()
  if [ -f "$SSL_BUNDLE" ]; then
    CA_FILES+=("$SSL_BUNDLE")
  fi
  if [ "${#EXTRA_CA_FILES[@]}" -gt 0 ]; then
    CA_FILES+=("${EXTRA_CA_FILES[@]}")
  fi

  if [ "${#CA_FILES[@]}" -gt 0 ]; then
    CUR_CA_FP="$(sha256sum "${CA_FILES[@]}" "$BASE_CACERTS" | sort | sha256sum | awk '{print $1}')|${BASE_CACERTS}"
  else
    CUR_CA_FP="none|${BASE_CACERTS}"
  fi
  OLD_CA_FP="$(cat "$CA_FP_FILE" 2>/dev/null || true)"

  # Fast-path content check: after VM wake, mtimes on /etc/ssl/certs/ca-certificates.crt
  # or /usr/local/share/ca-certificates/*.crt can be newer than system cacerts even when
  # every local CA cert is already present inside /etc/ssl/certs/java/cacerts.
  if [ "$JDK_USES_SYSTEM_CACERTS" -eq 1 ]; then
    if [ "$OLD_CA_FP" = "system|${CUR_CA_FP}" ]; then
      USE_SYSTEM_CACERTS=1
    elif [ "${#EXTRA_CA_FILES[@]}" -gt 0 ] && command -v python3 >/dev/null 2>&1; then
      if python3 -S - "$SYSTEM_JAVA_CACERTS" "${EXTRA_CA_FILES[@]}" <<'PY'
import base64, re, sys
cacerts = open(sys.argv[1], "rb").read()
found_any = False
for p in sys.argv[2:]:
    pem = open(p, "r", encoding="utf-8", errors="ignore").read()
    for b in re.findall(r"-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----", pem, re.S):
        found_any = True
        if base64.b64decode(b) not in cacerts:
            sys.exit(1)
sys.exit(0 if found_any else 1)
PY
      then
        USE_SYSTEM_CACERTS=1
        printf 'system|%s\n' "$CUR_CA_FP" > "$CA_FP_FILE"
      fi
    fi
  fi

  if [ "$USE_SYSTEM_CACERTS" -eq 0 ]; then
    if [ "$OLD_CA_FP" != "custom|${CUR_CA_FP}" ] || [ ! -f "$TRUSTSTORE" ]; then
      TMP_DIR="$(mktemp -d)"
      trap 'rm -rf "$TMP_DIR"' EXIT
      TMP_JKS="$TMP_DIR/proxy-cacerts.jks"
      cp "$BASE_CACERTS" "$TMP_JKS"
      chmod 644 "$TMP_JKS"

      import_pem_bundle() {
        local src="$1"
        local max_small="$2"
        local tail_large="$3"
        local count
        count="$(grep -c "BEGIN CERTIFICATE" "$src" 2>/dev/null || true)"
        [ "$count" -eq 0 ] && return 0
        local start_idx=1
        if [ "$count" -gt "$max_small" ]; then
          start_idx=$((count - tail_large + 1))
        fi
        awk -v outdir="$TMP_DIR" -v start="$start_idx" '
          /-----BEGIN CERTIFICATE-----/ { n++; in_cert = (n >= start); if (in_cert) fname = outdir "/cert_" n ".pem" }
          in_cert { print > fname }
          /-----END CERTIFICATE-----/ { if (in_cert) { close(fname); in_cert = 0 } }
        ' "$src"
      }

      if [ "$SSL_BUNDLE" != "/etc/ssl/certs/ca-certificates.crt" ] || [ "${#EXTRA_CA_FILES[@]}" -eq 0 ]; then
        if [ -f "$SSL_BUNDLE" ]; then
          import_pem_bundle "$SSL_BUNDLE" 16 5
        fi
      fi
      for cert in "${EXTRA_CA_FILES[@]}"; do
        cp "$cert" "$TMP_DIR/extra_$(basename "$cert").pem"
      done

      SEEN_HASHES=""
      IDX=0
      for pem in "$TMP_DIR"/*.pem; do
        [ -f "$pem" ] || continue
        CERT_HASH="$(sha256sum "$pem" | awk '{print $1}')"
        case "$SEEN_HASHES" in
          *"$CERT_HASH"*) continue ;;
        esac
        SEEN_HASHES="$SEEN_HASHES $CERT_HASH"
        IDX=$((IDX + 1))
        "$JAVA_HOME_DIR/bin/keytool" -importcert -noprompt -trustcacerts \
          -alias "local-proxy-ca-$IDX" \
          -file "$pem" \
          -keystore "$TMP_JKS" \
          -storepass changeit >/dev/null 2>&1 || true
      done

      cp "$TMP_JKS" "$TRUSTSTORE.tmp.$$"
      mv "$TRUSTSTORE.tmp.$$" "$TRUSTSTORE"
      if [ -w "$JAVA_HOME_DIR/lib/security/cacerts" ]; then
        cp "$TRUSTSTORE" "$JAVA_HOME_DIR/lib/security/cacerts"
      fi
      printf 'custom|%s\n' "$CUR_CA_FP" > "$CA_FP_FILE"
    fi
    [ -f "$TRUSTSTORE" ] && ACTIVE_TRUSTSTORE="$TRUSTSTORE"
  fi
fi

# 2. Parse HTTP_PROXY / HTTPS_PROXY and update managed blocks
parse_proxy_host_port() {
  local raw="$1"
  raw="${raw#http://}"
  raw="${raw#https://}"
  raw="${raw%%/*}"
  raw="${raw##*@}"
  local host="${raw%:*}"
  local port="${raw##*:}"
  if [ -n "$host" ] && [ -n "$port" ] && [ "$host" != "$port" ]; then
    printf '%s %s\n' "$host" "$port"
  fi
}

HTTP_PROXY_RAW="${HTTP_PROXY:-${http_proxy:-}}"
HTTPS_PROXY_RAW="${HTTPS_PROXY:-${https_proxy:-$HTTP_PROXY_RAW}}"

HTTP_HOST=""
HTTP_PORT=""
HTTPS_HOST=""
HTTPS_PORT=""

if [ -n "$HTTP_PROXY_RAW" ]; then
  read -r HTTP_HOST HTTP_PORT < <(parse_proxy_host_port "$HTTP_PROXY_RAW") || true
fi
if [ -n "$HTTPS_PROXY_RAW" ]; then
  read -r HTTPS_HOST HTTPS_PORT < <(parse_proxy_host_port "$HTTPS_PROXY_RAW") || true
fi

CUR_PROXY_FP="${HTTP_HOST}:${HTTP_PORT}|${HTTPS_HOST}:${HTTPS_PORT}|${ACTIVE_TRUSTSTORE:-system}"
OLD_PROXY_FP="$(cat "$PROXY_FP_FILE" 2>/dev/null || true)"

if [ "$CUR_PROXY_FP" != "$OLD_PROXY_FP" ] || [ ! -f "$GRADLE_PROPS" ] || [ ! -f "$PROXY_ENV_FILE" ]; then
  TMP_PROPS="$GRADLE_PROPS.tmp.$$"
  if [ -f "$GRADLE_PROPS" ]; then
    awk '
      /^# BEGIN WEMI-ENV-SETUP$/ { skip = 1; next }
      /^# END WEMI-ENV-SETUP$/ { skip = 0; next }
      !skip { print }
    ' "$GRADLE_PROPS" > "$TMP_PROPS"
  else
    : > "$TMP_PROPS"
  fi

  {
    echo "# BEGIN WEMI-ENV-SETUP"
    if [ -n "$HTTP_HOST" ] && [ -n "$HTTP_PORT" ]; then
      echo "systemProp.http.proxyHost=$HTTP_HOST"
      echo "systemProp.http.proxyPort=$HTTP_PORT"
      echo "systemProp.http.nonProxyHosts=localhost|127.0.0.1|[::1]"
    fi
    if [ -n "$HTTPS_HOST" ] && [ -n "$HTTPS_PORT" ]; then
      echo "systemProp.https.proxyHost=$HTTPS_HOST"
      echo "systemProp.https.proxyPort=$HTTPS_PORT"
      echo "systemProp.https.nonProxyHosts=localhost|127.0.0.1|[::1]"
    fi
    if [ -n "$ACTIVE_TRUSTSTORE" ] && [ -f "$ACTIVE_TRUSTSTORE" ]; then
      echo "systemProp.javax.net.ssl.trustStore=$ACTIVE_TRUSTSTORE"
      echo "systemProp.javax.net.ssl.trustStorePassword=changeit"
    fi
    echo "# END WEMI-ENV-SETUP"
  } >> "$TMP_PROPS"
  mv "$TMP_PROPS" "$GRADLE_PROPS"

  TMP_ENV="$PROXY_ENV_FILE.tmp.$$"
  GOPTS=""
  if [ -n "$HTTP_HOST" ] && [ -n "$HTTP_PORT" ]; then
    GOPTS="$GOPTS -Dhttp.proxyHost=$HTTP_HOST -Dhttp.proxyPort=$HTTP_PORT -Dhttp.nonProxyHosts=localhost|127.0.0.1|[::1]"
  fi
  if [ -n "$HTTPS_HOST" ] && [ -n "$HTTPS_PORT" ]; then
    GOPTS="$GOPTS -Dhttps.proxyHost=$HTTPS_HOST -Dhttps.proxyPort=$HTTPS_PORT -Dhttps.nonProxyHosts=localhost|127.0.0.1|[::1]"
  fi
  if [ -n "$ACTIVE_TRUSTSTORE" ] && [ -f "$ACTIVE_TRUSTSTORE" ]; then
    GOPTS="$GOPTS -Djavax.net.ssl.trustStore=$ACTIVE_TRUSTSTORE -Djavax.net.ssl.trustStorePassword=changeit"
  fi
  GOPTS="${GOPTS# }"
  if [ -n "$GOPTS" ]; then
    {
      printf 'export GRADLE_OPTS="%s"\n' "$GOPTS"
      if [ -n "$ACTIVE_TRUSTSTORE" ] && [ -f "$ACTIVE_TRUSTSTORE" ]; then
        printf 'export SDKMANAGER_OPTS="-Djavax.net.ssl.trustStore=%s -Djavax.net.ssl.trustStorePassword=changeit"\n' "$ACTIVE_TRUSTSTORE"
      fi
    } > "$TMP_ENV"
  else
    {
      echo 'unset GRADLE_OPTS'
      echo 'unset SDKMANAGER_OPTS'
    } > "$TMP_ENV"
  fi
  mv "$TMP_ENV" "$PROXY_ENV_FILE"

  printf '%s\n' "$CUR_PROXY_FP" > "$PROXY_FP_FILE"
fi
EOF
chmod +x "$TMP_SYNC"
if [ -x "$SYNC_HELPER" ] && cmp -s "$TMP_SYNC" "$SYNC_HELPER"; then
  rm -f "$TMP_SYNC"
else
  mv -f "$TMP_SYNC" "$SYNC_HELPER"
fi

# Ensure forked Gradle Test JVMs (e.g., Robolectric on a cache miss) inherit proxy & trustStore props,
# and prefer Google's Maven Central GCS mirror ahead of repo.maven.apache.org to avoid HTTP 429 rate limits on cloud VMs.
GRADLE_INIT_DIR="$GRADLE_DIR/init.d"
GRADLE_INIT_SCRIPT="$GRADLE_INIT_DIR/00-proxy-and-certs.gradle"
mkdir -p "$GRADLE_INIT_DIR"
TMP_INIT="${GRADLE_INIT_SCRIPT}.tmp.$$"
cat <<'EOF' > "$TMP_INIT"
beforeSettings { settings ->
    def mirrorUrl = "https://maven-central.storage-download.googleapis.com/maven2/"
    [settings.pluginManagement.repositories, settings.dependencyResolutionManagement.repositories].each { repos ->
        repos.all { repo ->
            if (repo instanceof MavenArtifactRepository && repo.url.toString().startsWith("https://repo.maven.apache.org/maven2")) {
                repo.url = uri(mirrorUrl)
            }
        }
    }
}

allprojects {
    tasks.withType(Test).configureEach {
        [
            "javax.net.ssl.trustStore",
            "javax.net.ssl.trustStorePassword",
            "http.proxyHost",
            "http.proxyPort",
            "https.proxyHost",
            "https.proxyPort",
            "http.nonProxyHosts",
            "https.nonProxyHosts"
        ].each { key ->
            def val = System.getProperty(key)
            if (val != null && !val.isEmpty()) {
                systemProperty(key, val)
            }
        }
    }
}
EOF
if [ -f "$GRADLE_INIT_SCRIPT" ] && cmp -s "$TMP_INIT" "$GRADLE_INIT_SCRIPT"; then
  rm -f "$TMP_INIT"
else
  mv -f "$TMP_INIT" "$GRADLE_INIT_SCRIPT"
fi

# 5. Persist resolved environment variables & proxy sync hook idempotently at the
#    top of ~/.bashrc (ahead of any interactive-only 'case $- in' guard) without
#    deleting or rewriting the user's existing ~/.bashrc logic.
touch "$HOME/.bashrc"

TMP_BASHRC="$HOME/.bashrc.tmp.$$"
cat <<EOF > "$TMP_BASHRC"
# BEGIN WEMI-ENV-SETUP
export JAVA_HOME="$JAVA_HOME_DIR"
export ANDROID_HOME="$ANDROID_SDK_DIR"
export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
EOF
cat <<'EOF' >> "$TMP_BASHRC"
for _wemi_dir in "$JAVA_HOME/bin" "$ANDROID_HOME/cmdline-tools/latest/bin" "$ANDROID_HOME/platform-tools" "$HOME/.local/bin"; do
  case ":$PATH:" in
    *":$_wemi_dir:"*) ;;
    *) export PATH="$_wemi_dir:$PATH" ;;
  esac
done
unset _wemi_dir
if [ -z "${_WEMI_PROXY_SYNCED:-}" ] && [ -x "$HOME/.local/bin/wemi-sync-java-proxy.sh" ]; then
  export _WEMI_PROXY_SYNCED=1
  BASH_ENV= "$HOME/.local/bin/wemi-sync-java-proxy.sh" || true
fi
[ -f "$HOME/.gradle/proxy.env" ] && . "$HOME/.gradle/proxy.env"
# END WEMI-ENV-SETUP
EOF
awk '
  /^# BEGIN WEMI-ENV-SETUP$/ { skip = 1; next }
  /^# END WEMI-ENV-SETUP$/ { skip = 0; next }
  !skip { print }
' "$HOME/.bashrc" >> "$TMP_BASHRC"
if cmp -s "$TMP_BASHRC" "$HOME/.bashrc"; then
  rm -f "$TMP_BASHRC"
else
  mv -f "$TMP_BASHRC" "$HOME/.bashrc"
fi

# Activate in current process and sync proxy/truststore before any SDK/Gradle tool runs
export JAVA_HOME="$JAVA_HOME_DIR"
export ANDROID_HOME="$ANDROID_SDK_DIR"
export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
for _wemi_dir in "$JAVA_HOME/bin" "$ANDROID_HOME/cmdline-tools/latest/bin" "$ANDROID_HOME/platform-tools" "$LOCAL_BIN_DIR"; do
  case ":$PATH:" in
    *":$_wemi_dir:"*) ;;
    *) export PATH="$_wemi_dir:$PATH" ;;
  esac
done
unset _wemi_dir

BASH_ENV= "$SYNC_HELPER"
[ -f "$GRADLE_DIR/proxy.env" ] && . "$GRADLE_DIR/proxy.env"

# 6. Ensure Android SDK licenses are accepted; defer platform/build-tools delta
#    installation to AGP's built-in on-demand auto-download unless --install-sdk
#    (or --save-caches) is explicitly requested.
sdkmanager_proxy_args() {
  PROXY_ARGS=()
  local proxy_raw="${HTTPS_PROXY:-${https_proxy:-${HTTP_PROXY:-${http_proxy:-}}}}"
  if [ -n "$proxy_raw" ]; then
    local proxy_strip="${proxy_raw#http://}"
    proxy_strip="${proxy_strip#https://}"
    proxy_strip="${proxy_strip%%/*}"
    proxy_strip="${proxy_strip##*@}"
    local p_host="${proxy_strip%:*}"
    local p_port="${proxy_strip##*:}"
    if [ -n "$p_host" ] && [ -n "$p_port" ] && [ "$p_host" != "$p_port" ]; then
      PROXY_ARGS=(--proxy=http --proxy_host="$p_host" --proxy_port="$p_port")
    fi
  fi
}

if [ ! -f "$ANDROID_HOME/licenses/android-sdk-license" ]; then
  sdkmanager_proxy_args
  ( yes || true ) | sdkmanager --sdk_root="$ANDROID_HOME" "${PROXY_ARGS[@]}" --licenses >/dev/null || true
fi

if [ "$INSTALL_SDK" -eq 1 ]; then
  MISSING_PKGS=()
  [ -x "$ANDROID_HOME/platform-tools/adb" ] || MISSING_PKGS+=("platform-tools")
  [ -f "$ANDROID_HOME/platforms/android-36/android.jar" ] || MISSING_PKGS+=("platforms;android-36")
  [ -f "$ANDROID_HOME/platforms/android-36.1/android.jar" ] || MISSING_PKGS+=("platforms;android-36.1")
  [ -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ] || MISSING_PKGS+=("build-tools;36.0.0")

  if [ "${#MISSING_PKGS[@]}" -gt 0 ]; then
    echo "[env-setup] Installing Android SDK packages: ${MISSING_PKGS[*]}"
    sdkmanager_proxy_args
    ( yes || true ) | sdkmanager --sdk_root="$ANDROID_HOME" "${PROXY_ARGS[@]}" "${MISSING_PKGS[@]}"
  fi
fi

# 7. Pre-warm Gradle wrapper only when saving shared caches (invoke via sh to keep 100644 mode)
GRADLE_VER="$(sed -n 's/.*gradle-\([0-9.]*\)-bin\.zip.*/\1/p' "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -n 1 || true)"
if [ -f "$REPO_ROOT/gradlew" ]; then
  if [ "$SAVE_CACHES" -eq 1 ] && { [ -z "$GRADLE_VER" ] || [ ! -d "$GRADLE_DIR/wrapper/dists/gradle-${GRADLE_VER}-bin" ]; }; then
    (cd "$REPO_ROOT" && sh ./gradlew --version --no-daemon >/dev/null)
  fi
fi

# 7b. Idempotently install local commit-msg attribution hook when inside a git checkout
COMMIT_VALIDATOR="$REPO_ROOT/.agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh"
if [ -d "$REPO_ROOT/.git" ] && [ -f "$COMMIT_VALIDATOR" ]; then
  if [ ! -x "$REPO_ROOT/.git/hooks/commit-msg" ] || ! grep -q "validate_commit_msg.sh" "$REPO_ROOT/.git/hooks/commit-msg" 2>/dev/null; then
    (cd "$REPO_ROOT" && bash "$COMMIT_VALIDATOR" --install-hook >/dev/null 2>&1) || true
  fi
fi

# 8. Refresh persistent shared caches only when --save-caches is explicitly requested
if [ "$SAVE_CACHES" -eq 1 ] && [ -n "$CACHE_DIR" ]; then
  if [ "$JAVA_HOME_DIR" = "$DEFAULT_LOCAL_JDK" ] && [ -x "$JAVA_HOME_DIR/bin/java" ]; then
    echo "[env-setup] Saving JDK 21 archive to $JDK_CACHE..."
    save_archive_atomic "$JDK_CACHE" -cf - -C "$HOME/.local" jdk-21 || true
  fi

  if [ "$ANDROID_SDK_DIR" = "$DEFAULT_LOCAL_SDK" ] && has_required_sdk "$ANDROID_SDK_DIR"; then
    echo "[env-setup] Saving Android SDK 36/36.1 archive to $SDK_CACHE..."
    save_archive_atomic "$SDK_CACHE" -cf - -C "$HOME" android-sdk || true
  fi

  if [ -d "$M2_DIR/repository/org/robolectric/android-all-instrumented" ]; then
    echo "[env-setup] Saving Robolectric SDK 36 archive to $M2_CACHE..."
    save_archive_atomic "$M2_CACHE" -cf - -C "$M2_DIR" repository/org/robolectric || true
  fi

  if [ -d "$GRADLE_DIR/caches/modules-2/files-2.1" ]; then
    echo "[env-setup] Saving warm Gradle dependency cache (${DEPS_KEY}) to $GRADLE_CACHE..."
    [ -f "$REPO_ROOT/gradlew" ] && (cd "$REPO_ROOT" && sh ./gradlew --stop >/dev/null 2>&1 || true)
    GRADLE_TAR_PATHS=()
    [ -d "$GRADLE_DIR/wrapper/dists" ] && GRADLE_TAR_PATHS+=("wrapper/dists")
    [ -d "$GRADLE_DIR/caches/modules-2" ] && GRADLE_TAR_PATHS+=("caches/modules-2")
    [ -n "$GRADLE_VER" ] && [ -d "$GRADLE_DIR/caches/$GRADLE_VER" ] && GRADLE_TAR_PATHS+=("caches/$GRADLE_VER")
    [ -d "$GRADLE_DIR/caches/journal-1" ] && GRADLE_TAR_PATHS+=("caches/journal-1")
    [ -d "$GRADLE_DIR/caches/build-cache-1" ] && GRADLE_TAR_PATHS+=("caches/build-cache-1")
    if [ "${#GRADLE_TAR_PATHS[@]}" -gt 0 ]; then
      if save_archive_atomic "$GRADLE_CACHE" \
        --exclude='*.lock' --exclude='daemon' --exclude='notifications' \
        --exclude='workers' --exclude='kotlin-profile' --exclude='.tmp' \
        --exclude='proxy-*' --exclude='gradle.properties' \
        -cf - -C "$GRADLE_DIR" "${GRADLE_TAR_PATHS[@]}"; then
        # Prune superseded Gradle cache snapshots
        for old in "$CACHE_DIR"/gradle-warm-cache-*."$ARCHIVE_EXT"; do
          [ -f "$old" ] && [ "$old" != "$GRADLE_CACHE" ] && rm -f "$old"
        done
      fi
    fi
  fi
fi

JAVA_VER_SUMMARY="$(sed -n 's/^JAVA_VERSION="\([^"]*\)"/\1/p' "$JAVA_HOME/release" 2>/dev/null | head -n 1 || true)"
if [ -n "$JAVA_VER_SUMMARY" ]; then
  JAVA_VER_SUMMARY="jdk-$JAVA_VER_SUMMARY"
else
  JAVA_VER_SUMMARY="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
fi
SDK_STATE="on-demand"
has_required_sdk "$ANDROID_HOME" && SDK_STATE="ready"

echo "[env-setup] Ready: $JAVA_VER_SUMMARY | JAVA_HOME=$JAVA_HOME | ANDROID_HOME=$ANDROID_HOME ($SDK_STATE) | cache=${CACHE_DIR:-disabled} (deps=${DEPS_KEY})"
