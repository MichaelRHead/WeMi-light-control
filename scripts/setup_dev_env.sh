#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Fast, Cache-Backed Cloud & Developer Environment Setup
# ==============================================================================
# Configures Temurin JDK 21, Android SDK 36/36.1, Robolectric SDK 36 artifacts,
# Gradle 9.3.1 warm caches, and dynamic JVM/Gradle proxy & TLS CA syncing
# completely in user space ($HOME) without root/sudo.
#
# Performance Architecture:
#   - On fresh cloud VMs, restores immutable lz4/gzip archives in parallel from
#     persistent $HOME/.gemini/caches/wemi-android-v1/ onto fast local disk
#     ($HOME on xfs) in ~15-20s, avoiding virtiofs lock contention and avoiding
#     multi-minute cold Maven/SDK downloads.
#   - Falls back cleanly to direct network downloads on a cache miss or outside
#     Antigravity VMs (when $HOME/.gemini is absent).
#   - Pass --save-caches after a warm build/test run to refresh shared caches.
#
# Idempotent and non-interactive: safe to run repeatedly on fresh cloud VMs and
# across VM sleep/wake cycles.
# ==============================================================================
set -euo pipefail

SAVE_CACHES=0
for arg in "$@"; do
  case "$arg" in
    --save-caches) SAVE_CACHES=1 ;;
    -h|--help)
      echo "Usage: $0 [--save-caches]"
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

JAVA_HOME_DIR="$HOME/.local/jdk-21"
ANDROID_SDK_DIR="$HOME/android-sdk"
LOCAL_BIN_DIR="$HOME/.local/bin"
GRADLE_DIR="$HOME/.gradle"
M2_DIR="$HOME/.m2"
SYNC_HELPER="$LOCAL_BIN_DIR/wemi-sync-java-proxy.sh"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

mkdir -p "$LOCAL_BIN_DIR" "$HOME/.local" "$ANDROID_SDK_DIR/cmdline-tools" "$GRADLE_DIR" "$M2_DIR"

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

# 1. Fast parallel restore from persistent shared cache onto local fast disk
RESTORE_PIDS=()
RESTORE_NAMES=()

need_sdk_restore() {
  [ ! -x "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ] ||
  [ ! -x "$ANDROID_SDK_DIR/platform-tools/adb" ] ||
  [ ! -f "$ANDROID_SDK_DIR/platforms/android-36/android.jar" ] ||
  [ ! -f "$ANDROID_SDK_DIR/platforms/android-36.1/android.jar" ] ||
  [ ! -x "$ANDROID_SDK_DIR/build-tools/36.0.0/aapt2" ] ||
  [ ! -x "$ANDROID_SDK_DIR/build-tools/36.1.0/aapt2" ]
}

if [ -n "$CACHE_DIR" ]; then
  if [ ! -x "$JAVA_HOME_DIR/bin/java" ] && [ -f "$JDK_CACHE" ]; then
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

  if need_sdk_restore && [ -f "$SDK_CACHE" ]; then
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

# 2. Network fallback: Install Temurin JDK 21 if still missing
JDK_FRESHLY_INSTALLED=0
if [ ! -x "$JAVA_HOME_DIR/bin/java" ]; then
  echo "[env-setup] Downloading Temurin JDK 21 (${ADOPTIUM_ARCH}) into $JAVA_HOME_DIR..."
  TMP_JDK="$(mktemp /tmp/temurin-jdk21-XXXXXX.tar.gz)"
  curl -fSL \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/${ADOPTIUM_ARCH}/jdk/hotspot/normal/eclipse" \
    -o "$TMP_JDK"
  rm -rf "${JAVA_HOME_DIR}.tmp"
  mkdir -p "${JAVA_HOME_DIR}.tmp"
  tar -xzf "$TMP_JDK" -C "${JAVA_HOME_DIR}.tmp" --strip-components=1
  rm -f "$TMP_JDK"
  rm -rf "$JAVA_HOME_DIR"
  mv "${JAVA_HOME_DIR}.tmp" "$JAVA_HOME_DIR"
  JDK_FRESHLY_INSTALLED=1
fi

if [ ! -f "$JAVA_HOME_DIR/lib/security/cacerts.orig" ]; then
  cp "$JAVA_HOME_DIR/lib/security/cacerts" "$JAVA_HOME_DIR/lib/security/cacerts.orig"
fi

# 3. Network fallback: Install Android SDK cmdline-tools if still missing
SDK_FRESHLY_INSTALLED=0
if [ ! -x "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[env-setup] Downloading Android SDK command-line tools into $ANDROID_SDK_DIR..."
  TMP_CLI="$(mktemp /tmp/android-cmdline-tools-XXXXXX.zip)"
  curl -fSL "$CMDLINE_TOOLS_URL" -o "$TMP_CLI"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  mkdir -p "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  unzip -q "$TMP_CLI" -d "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  rm -f "$TMP_CLI"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest"
  mv "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  SDK_FRESHLY_INSTALLED=1
fi

# 4. Install/update fast (<15ms) dynamic proxy & TLS CA sync helper
TMP_SYNC="${SYNC_HELPER}.tmp.$$"
cat <<'EOF' > "$TMP_SYNC"
#!/usr/bin/env bash
# Synchronize JDK cacerts, ~/.gradle/gradle.properties, and ~/.gradle/proxy.env
# with the active HTTP(S) proxy and local proxy CA certificates.
set -euo pipefail

JAVA_HOME_DIR="${JAVA_HOME:-$HOME/.local/jdk-21}"
GRADLE_DIR="$HOME/.gradle"
TRUSTSTORE="$GRADLE_DIR/proxy-cacerts.jks"
CA_FP_FILE="$GRADLE_DIR/proxy-ca.fingerprint"
PROXY_FP_FILE="$GRADLE_DIR/proxy-env.fingerprint"
GRADLE_PROPS="$GRADLE_DIR/gradle.properties"
PROXY_ENV_FILE="$GRADLE_DIR/proxy.env"
SSL_BUNDLE="${SSL_CERT_FILE:-/etc/ssl/certs/ca-certificates.crt}"

mkdir -p "$GRADLE_DIR"

# 1. Sync proxy/custom CA certificates into user-space JDK cacerts & Gradle truststore
if [ -x "$JAVA_HOME_DIR/bin/keytool" ] && [ -f "$JAVA_HOME_DIR/lib/security/cacerts" ]; then
  if [ ! -f "$JAVA_HOME_DIR/lib/security/cacerts.orig" ]; then
    cp "$JAVA_HOME_DIR/lib/security/cacerts" "$JAVA_HOME_DIR/lib/security/cacerts.orig"
  fi

  CA_FILES=()
  if [ -f "$SSL_BUNDLE" ]; then
    CA_FILES+=("$SSL_BUNDLE")
  fi
  for cert in /usr/local/share/ca-certificates/*.crt; do
    [ -f "$cert" ] && CA_FILES+=("$cert")
  done

  if [ "${#CA_FILES[@]}" -gt 0 ]; then
    CUR_CA_FP="$(sha256sum "${CA_FILES[@]}" | sort | sha256sum | awk '{print $1}')"
  else
    CUR_CA_FP="none"
  fi
  OLD_CA_FP="$(cat "$CA_FP_FILE" 2>/dev/null || true)"

  if [ "$CUR_CA_FP" != "$OLD_CA_FP" ] || [ ! -f "$TRUSTSTORE" ]; then
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "$TMP_DIR"' EXIT
    TMP_JKS="$TMP_DIR/proxy-cacerts.jks"
    cp "$JAVA_HOME_DIR/lib/security/cacerts.orig" "$TMP_JKS"
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

    if [ -f "$SSL_BUNDLE" ]; then
      import_pem_bundle "$SSL_BUNDLE" 16 5
    fi
    for cert in /usr/local/share/ca-certificates/*.crt; do
      [ -f "$cert" ] || continue
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
    cp "$TRUSTSTORE" "$JAVA_HOME_DIR/lib/security/cacerts"
    printf '%s\n' "$CUR_CA_FP" > "$CA_FP_FILE"
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

CUR_PROXY_FP="${HTTP_HOST}:${HTTP_PORT}|${HTTPS_HOST}:${HTTPS_PORT}|${TRUSTSTORE}"
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
    if [ -f "$TRUSTSTORE" ]; then
      echo "systemProp.javax.net.ssl.trustStore=$TRUSTSTORE"
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
  if [ -f "$TRUSTSTORE" ]; then
    GOPTS="$GOPTS -Djavax.net.ssl.trustStore=$TRUSTSTORE -Djavax.net.ssl.trustStorePassword=changeit"
  fi
  GOPTS="${GOPTS# }"
  if [ -n "$GOPTS" ]; then
    printf 'export GRADLE_OPTS="%s"\n' "$GOPTS" > "$TMP_ENV"
  else
    : > "$TMP_ENV"
  fi
  mv "$TMP_ENV" "$PROXY_ENV_FILE"

  printf '%s\n' "$CUR_PROXY_FP" > "$PROXY_FP_FILE"
fi
EOF
chmod +x "$TMP_SYNC"
mv "$TMP_SYNC" "$SYNC_HELPER"

# 5. Persist environment variables & proxy sync hook idempotently in ~/.bashrc
touch "$HOME/.bashrc"
sed -i '/case \$- in/,/esac/d' "$HOME/.bashrc" 2>/dev/null || true

TMP_BASHRC="$HOME/.bashrc.tmp.$$"
awk '
  /^# BEGIN WEMI-ENV-SETUP$/ { skip = 1; next }
  /^# END WEMI-ENV-SETUP$/ { skip = 0; next }
  !skip { print }
' "$HOME/.bashrc" > "$TMP_BASHRC"

cat <<'EOF' >> "$TMP_BASHRC"
# BEGIN WEMI-ENV-SETUP
export JAVA_HOME="$HOME/.local/jdk-21"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
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
mv "$TMP_BASHRC" "$HOME/.bashrc"

# Activate in current process and sync proxy/truststore before invoking sdkmanager or gradlew
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

# 6. Network fallback: Install missing Android SDK 36 / 36.1 packages
MISSING_PKGS=()
[ -x "$ANDROID_HOME/platform-tools/adb" ] || MISSING_PKGS+=("platform-tools")
[ -f "$ANDROID_HOME/platforms/android-36/android.jar" ] || MISSING_PKGS+=("platforms;android-36")
[ -f "$ANDROID_HOME/platforms/android-36.1/android.jar" ] || MISSING_PKGS+=("platforms;android-36.1")
[ -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ] || MISSING_PKGS+=("build-tools;36.0.0")
[ -x "$ANDROID_HOME/build-tools/36.1.0/aapt2" ] || MISSING_PKGS+=("build-tools;36.1.0")

if [ "${#MISSING_PKGS[@]}" -gt 0 ]; then
  echo "[env-setup] Installing Android SDK packages: ${MISSING_PKGS[*]}"
  PROXY_ARGS=()
  PROXY_RAW="${HTTPS_PROXY:-${https_proxy:-${HTTP_PROXY:-${http_proxy:-}}}}"
  if [ -n "$PROXY_RAW" ]; then
    PROXY_STRIP="${PROXY_RAW#http://}"
    PROXY_STRIP="${PROXY_STRIP#https://}"
    PROXY_STRIP="${PROXY_STRIP%%/*}"
    PROXY_STRIP="${PROXY_STRIP##*@}"
    P_HOST="${PROXY_STRIP%:*}"
    P_PORT="${PROXY_STRIP##*:}"
    if [ -n "$P_HOST" ] && [ -n "$P_PORT" ] && [ "$P_HOST" != "$P_PORT" ]; then
      PROXY_ARGS=(--proxy=http --proxy_host="$P_HOST" --proxy_port="$P_PORT")
    fi
  fi
  ( yes || true ) | sdkmanager --sdk_root="$ANDROID_HOME" "${PROXY_ARGS[@]}" --licenses >/dev/null || true
  sdkmanager --sdk_root="$ANDROID_HOME" "${PROXY_ARGS[@]}" "${MISSING_PKGS[@]}"
  SDK_FRESHLY_INSTALLED=1
fi

# 7. Ensure gradlew is executable and pre-warm Gradle wrapper only if absent
GRADLE_VER="$(sed -n 's/.*gradle-\([0-9.]*\)-bin\.zip.*/\1/p' "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -n 1 || true)"
if [ -f "$REPO_ROOT/gradlew" ]; then
  chmod +x "$REPO_ROOT/gradlew"
  if [ -z "$GRADLE_VER" ] || [ ! -d "$GRADLE_DIR/wrapper/dists/gradle-${GRADLE_VER}-bin" ]; then
    (cd "$REPO_ROOT" && ./gradlew --version --no-daemon >/dev/null)
  fi
fi

# 8. Populate or refresh persistent shared caches when missing or --save-caches is passed
if [ -n "$CACHE_DIR" ]; then
  if [ "$SAVE_CACHES" -eq 1 ] || [ "$JDK_FRESHLY_INSTALLED" -eq 1 ] || [ ! -f "$JDK_CACHE" ]; then
    if [ -x "$JAVA_HOME_DIR/bin/java" ]; then
      echo "[env-setup] Saving JDK 21 archive to $JDK_CACHE..."
      save_archive_atomic "$JDK_CACHE" -cf - -C "$HOME/.local" jdk-21 || true
    fi
  fi

  if [ "$SAVE_CACHES" -eq 1 ] || [ "$SDK_FRESHLY_INSTALLED" -eq 1 ] || [ ! -f "$SDK_CACHE" ]; then
    if ! need_sdk_restore; then
      echo "[env-setup] Saving Android SDK 36/36.1 archive to $SDK_CACHE..."
      save_archive_atomic "$SDK_CACHE" -cf - -C "$HOME" android-sdk || true
    fi
  fi

  if [ "$SAVE_CACHES" -eq 1 ] || [ ! -f "$M2_CACHE" ]; then
    if [ -d "$M2_DIR/repository/org/robolectric/android-all-instrumented" ]; then
      echo "[env-setup] Saving Robolectric SDK 36 archive to $M2_CACHE..."
      save_archive_atomic "$M2_CACHE" -cf - -C "$M2_DIR" repository/org/robolectric || true
    fi
  fi

  if [ "$SAVE_CACHES" -eq 1 ] || [ ! -f "$GRADLE_CACHE" ]; then
    if [ -d "$GRADLE_DIR/caches/modules-2/files-2.1" ]; then
      echo "[env-setup] Saving warm Gradle dependency cache (${DEPS_KEY}) to $GRADLE_CACHE..."
      [ -f "$REPO_ROOT/gradlew" ] && (cd "$REPO_ROOT" && ./gradlew --stop >/dev/null 2>&1 || true)
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
fi

echo "[env-setup] Ready: $(java -version 2>&1 | head -n 1) | ANDROID_HOME=$ANDROID_HOME | cache=${CACHE_DIR:-disabled} (deps=${DEPS_KEY})"
