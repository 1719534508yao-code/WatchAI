#!/bin/bash
# 手动构建（不用 Gradle），产出极小 APK
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
OUT="$ROOT/build"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)}"
export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"

BT="$SDK/build-tools/34.0.0"
APK_PLATFORM="$SDK/platforms/android-27/android.jar"
[ -f "$APK_PLATFORM" ] || APK_PLATFORM="$(ls -d $SDK/platforms/android-*/android.jar | sort -V | tail -1)"
PLATFORM_DIR="$(dirname "$APK_PLATFORM")"

echo "JAVA: $JAVA_HOME"
echo "SDK : $SDK"
echo "jar : $APK_PLATFORM"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

# 1. 编译资源
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"

# 2. 链接资源 + 生成清单
"$BT/aapt2" link -o "$OUT/base.apk" \
    -I "$APK_PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    -R "$OUT/res.zip" \
    --min-sdk-version 21 --target-sdk-version 27 \
    --auto-add-overlay

# 3. 编译 Java
find "$APP/src" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
    -bootclasspath "$APK_PLATFORM" -classpath "$APK_PLATFORM" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v "bootstrap class path" || true

# 4. 转 dex
"$BT/d8" --release --min-api 21 --lib "$APK_PLATFORM" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

# 5. 打包
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -qj "$OUT/unsigned.apk" classes.dex)

# 6. 对齐 + 签名
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$ROOT/debug.keystore" ]; then
    keytool -genkeypair -v -keystore "$ROOT/debug.keystore" \
        -alias watchai -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=watchai, OU=dev, O=dev, L=dev, S=dev, C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$ROOT/debug.keystore" --ks-pass pass:android \
    --key-pass pass:android --out "$ROOT/watchai.apk" "$OUT/aligned.apk"

echo
echo "✅ 产出: $ROOT/watchai.apk  ($(du -h "$ROOT/watchai.apk" | cut -f1))"
