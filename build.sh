#!/bin/bash
# Sestavení APK bez Gradlu a Android Studia: aapt2 -> javac -> d8 -> apksigner
#
# Potřebuješ JDK 17+ a tyto nástroje (cesty nastav proměnnými prostředí):
#   AAPT2        – aapt2 z Android SDK Build-Tools
#   ANDROID_JAR  – platforms/android-35/android.jar z Android SDK
#   D8_JAR       – d8.jar / r8.jar (obsahuje třídu com.android.tools.r8.D8)
#   APKSIGNER    – apksigner.jar z Android SDK Build-Tools
#   KEYSTORE     – podpisový klíč (když neexistuje, vytvoří se nový)
#   KS_PASS      – heslo ke klíči
#
# Příklad:
#   AAPT2=~/Android/Sdk/build-tools/35.0.0/aapt2 \
#   ANDROID_JAR=~/Android/Sdk/platforms/android-35/android.jar \
#   D8_JAR=~/Android/Sdk/build-tools/35.0.0/lib/d8.jar \
#   APKSIGNER=~/Android/Sdk/build-tools/35.0.0/lib/apksigner.jar \
#   KS_PASS=tajneheslo ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

: "${AAPT2:?Nastav AAPT2}"
: "${ANDROID_JAR:?Nastav ANDROID_JAR}"
: "${D8_JAR:?Nastav D8_JAR}"
: "${APKSIGNER:?Nastav APKSIGNER}"
: "${KS_PASS:?Nastav KS_PASS}"
KEYSTORE=${KEYSTORE:-svetylko.keystore}
KS_ALIAS=${KS_ALIAS:-svetylko}
VERSION_CODE=${VERSION_CODE:-5}
VERSION_NAME=${VERSION_NAME:-1.4}

rm -rf build && mkdir -p build/gen build/classes build/dex

echo "== aapt2 compile/link"
"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 30 --target-sdk-version 35 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    --java build/gen -o build/base.apk build/res.zip

echo "== javac"
find src build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 11 -cp "$ANDROID_JAR" -d build/classes @build/sources.txt

echo "== d8"
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 30 --lib "$ANDROID_JAR" \
    --output build/dex $(find build/classes -name '*.class')

echo "== package"
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -9 ../unsigned.apk classes.dex)

echo "== sign"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
      -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 36500 \
      -dname "CN=Svetylko, C=CZ"
fi
java -jar "$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KS_ALIAS" --v2-signing-enabled true --out build/Svetylko.apk build/unsigned.apk
java -jar "$APKSIGNER" verify build/Svetylko.apk
echo "Hotovo: build/Svetylko.apk"
