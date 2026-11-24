#!/bin/bash
set -e

echo "=== Fire TV Home Redirect Build Script ==="

SDK_JAR="${1:-/tmp/android-build/android.jar}"
R8_JAR="${2:-/tmp/android-build/r8.jar}"
APKTOOL_JAR="${3:-/tmp/apktool.jar}"
KEYSTORE="${4:-/tmp/test.keystore}"
KEYSTORE_PASS="${5:-123456}"
KEY_ALIAS="${6:-test}"

if [ ! -f "$SDK_JAR" ]; then
    echo "Error: android.jar not found at $SDK_JAR"
    echo "Usage: ./build.sh <path-to-android.jar> <path-to-r8.jar> <path-to-apktool.jar>"
    exit 1
fi

echo "[1/5] Cleaning and creating build directories..."
rm -rf build/classes build/dex
mkdir -p build/classes build/dex release

echo "[2/5] Compiling Java source files (Java 8 bytecode)..."
javac --release 8 -cp "$SDK_JAR" -d build/classes $(find app/src/main/java -name "*.java")

echo "[3/5] Converting class files to DEX (API 25)..."
java -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 25 --output build/dex $(find build/classes -name "*.class")

echo "[4/5] Packaging APK with apktool..."
java -jar "$APKTOOL_JAR" b app/src/main -o build/base.apk
cd build/dex
zip -j ../base.apk classes.dex
cd ../..

echo "[5/5] Signing APK with jarsigner..."
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" build/base.apk "$KEY_ALIAS"
cp build/base.apk release/HomeRedirect.apk

echo "=== BUILD SUCCESSFUL! Output: release/HomeRedirect.apk ==="
