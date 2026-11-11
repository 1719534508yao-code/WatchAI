#!/bin/bash
# 在与手表同规格的模拟器里跑起来：402x476 / 密度 200 / Android 8.1 (API 27)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$SDK/platform-tools:$SDK/emulator:$PATH"
export ANDROID_SDK_ROOT="$SDK"

AVD=watch402
PKG=com.yao.watchai

# ---------- 1. 首次创建 AVD ----------
if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo "创建 AVD $AVD ..."
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd \
        -n "$AVD" -k "system-images;android-27;default;arm64-v8a" --force >/dev/null

    CFG="$HOME/.android/avd/$AVD.avd/config.ini"
    # 覆盖成手表规格
    sed -i '' -e '/^hw\.lcd\./d' -e '/^skin\./d' "$CFG" 2>/dev/null || true
    cat >> "$CFG" <<EOF
hw.lcd.width=402
hw.lcd.height=476
hw.lcd.density=200
skin.name=402x476
skin.path=_no_skin
hw.ramSize=2048
hw.keyboard=yes
disk.dataPartition.size=2G
EOF
fi

# ---------- 2. 启动 ----------
ADB="$SDK/platform-tools/adb"
if ! "$ADB" devices | grep -q "emulator-"; then
    echo "启动模拟器 ..."
    nohup "$SDK/emulator/emulator" -avd "$AVD" -no-snapshot -no-boot-anim \
        -netdelay none -netspeed full > /tmp/watch402-emu.log 2>&1 &
fi

echo "等待开机 ..."
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done

# ---------- 3. 设置成手表尺寸 ----------
"$ADB" shell wm size 402x476
"$ADB" shell wm density 200

# ---------- 4. 编译 + 安装 ----------
[ -f "$ROOT/watchai.apk" ] || "$ROOT/build.sh"
"$ADB" install -r "$ROOT/watchai.apk"

# ---------- 5. 启动 ----------
"$ADB" shell am start -n "$PKG/.MainActivity"
echo "✅ 已启动"
