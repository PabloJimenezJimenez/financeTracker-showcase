#!/bin/bash
# Installs the Android SDK so ./gradlew test and lint work in Claude Code cloud sessions.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
PACKAGES=("platform-tools" "platforms;android-37.2" "build-tools;37.0.0")

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android command-line tools into $SDK" >&2
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK" "${PACKAGES[@]}" > /dev/null

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=\"$SDK\"" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=\"$SDK\"" >> "$CLAUDE_ENV_FILE"
fi

# Commits are authored by the repo owner, never by Claude (see CLAUDE.md).
git -C "${CLAUDE_PROJECT_DIR:-$(pwd)}" config user.name "PabloJimenezJimenez"
git -C "${CLAUDE_PROJECT_DIR:-$(pwd)}" config user.email "116873476+PabloJimenezJimenez@users.noreply.github.com"

# Cloud containers get rate-limited (HTTP 429) by Maven Central; try Google's mirror of it first.
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-central-mirror.gradle.kts" << 'GRADLE'
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
beforeSettings {
    pluginManagement.repositories { maven(mirror) }
    dependencyResolutionManagement.repositories { maven(mirror) }
}
// Robolectric downloads its android-all jars itself at test time.
allprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", mirror)
    }
}
GRADLE

# Point Gradle at the SDK and warm the dependency cache.
cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"
echo "sdk.dir=$SDK" > local.properties
if [ -x ./gradlew ]; then
  ANDROID_HOME="$SDK" ./gradlew --quiet help > /dev/null
fi
