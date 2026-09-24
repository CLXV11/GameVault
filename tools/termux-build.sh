#!/data/data/com.termux/files/usr/bin/bash
# GameVault - full Android build inside Termux (no root required).
# Tested path: OpenJDK 17 + Android cmdline-tools + Gradle 8.7.
set -euo pipefail

echo "==> Installing dependencies"
pkg update -y
pkg install -y openjdk-17 git wget unzip

export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  echo "==> Downloading Android cmdline-tools"
  cd "$HOME"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q commandlinetools-linux-11076708_latest.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

echo "==> Accepting licenses & installing SDK packages"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-34" "build-tools;34.0.0" "platform-tools"

echo "==> Generating Gradle wrapper"
if [ ! -f ./gradlew ]; then
  cd "$HOME"
  wget -q https://services.gradle.org/distributions/gradle-8.7-bin.zip
  unzip -q gradle-8.7-bin.zip
  cd - > /dev/null
  "$HOME/gradle-8.7/bin/gradle" wrapper --gradle-version 8.7
fi

echo "==> Running unit tests"
./gradlew testDebugUnitTest

echo "==> Building debug APK"
./gradlew assembleDebug

echo ""
echo "Done. APK: app/build/outputs/apk/debug/app-debug.apk"
ls -lh app/build/outputs/apk/debug/app-debug.apk
