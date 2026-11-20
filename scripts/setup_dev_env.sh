#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Repeatable Cloud & Developer Environment Setup
# ==============================================================================
# Installs Temurin JDK 21, Android SDK Command-Line Tools, Android SDK 36/36.1
# platforms & build-tools, and a dynamic JVM/Gradle proxy & TLS CA sync helper
# completely in user space ($HOME) without root/sudo.
#
# Idempotent and non-interactive: safe to run repeatedly on fresh cloud VMs and
# across VM sleep/wake cycles.
# ==============================================================================
set -euo pipefail

JAVA_HOME_DIR="$HOME/.local/jdk-21"
ANDROID_SDK_DIR="$HOME/android-sdk"
LOCAL_BIN_DIR="$HOME/.local/bin"
GRADLE_DIR="$HOME/.gradle"
SYNC_HELPER="$LOCAL_BIN_DIR/wemi-sync-java-proxy.sh"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

mkdir -p "$LOCAL_BIN_DIR" "$ANDROID_SDK_DIR/cmdline-tools" "$GRADLE_DIR"

# 1. Install Temurin JDK 21 into $HOME/.local/jdk-21 if not already present
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64) ADOPTIUM_ARCH="x64" ;;
  aarch64|arm64) ADOPTIUM_ARCH="aarch64" ;;
  *)
    echo "[env-setup] Unsupported CPU architecture: $ARCH" >&2
    exit 1
    ;;
esac

if [ ! -x "$JAVA_HOME_DIR/bin/java" ]; then
  echo "[env-setup] Installing Temurin JDK 21 (${ADOPTIUM_ARCH}) into $JAVA_HOME_DIR..."
  TMP_JDK="$(mktemp /tmp/temurin-jdk21-XXXXXX.tar.gz)"
  trap 'rm -f "${TMP_JDK:-}" "${TMP_CLI:-}"' EXIT
  curl -fSL \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/${ADOPTIUM_ARCH}/jdk/hotspot/normal/eclipse" \
    -o "$TMP_JDK"
  rm -rf "${JAVA_HOME_DIR}.tmp"
  mkdir -p "${JAVA_HOME_DIR}.tmp"
  tar -xzf "$TMP_JDK" -C "${JAVA_HOME_DIR}.tmp" --strip-components=1
  rm -f "$TMP_JDK"
  rm -rf "$JAVA_HOME_DIR"
  mv "${JAVA_HOME_DIR}.tmp" "$JAVA_HOME_DIR"
fi

if [ ! -f "$JAVA_HOME_DIR/lib/security/cacerts.orig" ]; then
  cp "$JAVA_HOME_DIR/lib/security/cacerts" "$JAVA_HOME_DIR/lib/security/cacerts.orig"
fi

# 2. Install Android SDK Command-Line Tools into $HOME/android-sdk/cmdline-tools/latest
if [ ! -x "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[env-setup] Installing Android SDK command-line tools into $ANDROID_SDK_DIR..."
  TMP_CLI="$(mktemp /tmp/android-cmdline-tools-XXXXXX.zip)"
  trap 'rm -f "${TMP_JDK:-}" "${TMP_CLI:-}"' EXIT
  curl -fSL "$CMDLINE_TOOLS_URL" -o "$TMP_CLI"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  mkdir -p "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  unzip -q "$TMP_CLI" -d "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
  rm -f "$TMP_CLI"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest"
  mv "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
  rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest.tmp"
fi

# 3. Install/update fast (<15ms) dynamic proxy & CA truststore sync helper
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

# 4. Persist environment variables & proxy sync hook idempotently in ~/.bashrc
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

# 5. Install required Android SDK 36 / 36.1 packages if any are missing
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
fi

# 6. Ensure gradlew is executable and pre-warm Gradle wrapper distribution
if [ -f "./gradlew" ]; then
  chmod +x ./gradlew
  ./gradlew --version --no-daemon >/dev/null
fi

echo "[env-setup] Ready: $(java -version 2>&1 | head -n 1) | ANDROID_HOME=$ANDROID_HOME"
