#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# 1. Locate Java / JBR
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/usr/local/android-studio/jbr" ]; then
        export JAVA_HOME="/usr/local/android-studio/jbr"
    elif [ -d "/opt/android-studio/jbr" ]; then
        export JAVA_HOME="/opt/android-studio/jbr"
    fi
fi

export PATH="$JAVA_HOME/bin:$PATH:$HOME/Android/Sdk/platform-tools"

# 2. Locate adb
if command -v adb >/dev/null 2>&1; then
    ADB_CMD="adb"
elif [ -x "$HOME/Android/Sdk/platform-tools/adb" ]; then
    ADB_CMD="$HOME/Android/Sdk/platform-tools/adb"
else
    echo "Error: adb not found. Ensure Android SDK platform-tools is installed."
    exit 1
fi

echo "==> Checking connected devices..."
CONNECTED_DEVICES=$("$ADB_CMD" devices | grep -v "List of" | grep "device$" | awk '{print $1}')

if [ -z "$CONNECTED_DEVICES" ]; then
    echo "Warning: No adb device is currently connected."
else
    echo "Device detected: $CONNECTED_DEVICES"
fi

echo "==> Building PillTracker debug APK..."
./gradlew assembleDebug

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi

if [ -n "$CONNECTED_DEVICES" ]; then
    echo "==> Installing PillTracker to device..."
    "$ADB_CMD" install -r "$APK_PATH"

    echo "==> Launching PillTracker..."
    "$ADB_CMD" shell am start -n com.scott.pilltracker/.MainActivity

    echo "✅ Done! PillTracker installed and launched on device."
else
    echo "✅ Build finished successfully: $APK_PATH"
    echo "Once your phone is connected, run: $ADB_CMD install -r \"$APK_PATH\""
fi
