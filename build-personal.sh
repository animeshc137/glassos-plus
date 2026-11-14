#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Use a standard JDK/Android SDK setup; do not depend on a developer's local workspace.
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT to Android SDK 34}"
export ANDROID_HOME
# Cinemeta requires no key; avoid embedding an unrelated inherited credential.
export TMDB_API_KEY=""
mkdir -p tests/classes
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d tests/classes \
  app/src/main/java/io/github/abhishekkaisar/glassos/LayoutMath.java \
  app/src/main/java/io/github/abhishekkaisar/glassos/TitleLinks.java \
  app/src/main/java/io/github/abhishekkaisar/glassos/HeroShuffleBag.java \
  app/src/main/java/io/github/abhishekkaisar/glassos/HeroCatalogSelection.java \
  app/src/main/java/io/github/abhishekkaisar/glassos/HomeRedirectPolicy.java \
  app/src/main/java/io/github/abhishekkaisar/glassos/ContinueLogic.java \
  app/src/main/java/io/github/abhishekkaisar/glassos/DirectHomeKeyPolicy.java \
  app/src/main/java/io/github/abhishekkaisar/glassos/RunningParse.java tests/PersonalizationCheck.java
"$JAVA_HOME/bin/java" -cp tests/classes PersonalizationCheck
./gradlew --no-daemon assembleDebug lint
# Every APK must carry the personal certificate, or the TV refuses to update the installed app ("signatures do not
# match"). The fingerprint is public; the key is not. CI sets GLASSOS_REQUIRE_PERSONAL_KEY=true, which makes a
# mismatch fail the build; elsewhere it only warns.
EXPECTED_CERT_SHA256="c69ea10051024a7e537357e66360b9c68992ff8ab2d04082dff0c4e55cd00d55"
APKSIGNER="$(ls "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
if [ -z "$APKSIGNER" ]; then
  echo "apksigner not found under $ANDROID_HOME/build-tools; cannot check the signing certificate" >&2
  if [ "${GLASSOS_REQUIRE_PERSONAL_KEY:-}" = "true" ]; then exit 1; fi
else
  ACTUAL_CERT_SHA256="$("$APKSIGNER" verify --print-certs app/build/outputs/apk/debug/app-debug.apk | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d '\r')"
  if [ "$ACTUAL_CERT_SHA256" = "$EXPECTED_CERT_SHA256" ]; then
    echo "APK is signed with the personal certificate ($ACTUAL_CERT_SHA256)"
  else
    echo "WARNING: the APK is NOT signed with the personal certificate (got $ACTUAL_CERT_SHA256); the TV will refuse to update the installed app." >&2
    if [ "${GLASSOS_REQUIRE_PERSONAL_KEY:-}" = "true" ]; then exit 1; fi
  fi
fi
python3 - <<'PY'
import xml.etree.ElementTree as E
from pathlib import Path
p = Path('app/build/reports/lint-results-debug.xml')
root = E.parse(p).getroot()
errors = [i for i in root.findall('issue') if i.get('severity') in ('Error', 'Fatal')]
# The official v1.0.2-beta also targets 28 for TV compatibility. This warning is
# a Google Play publication requirement, not an installation requirement here.
# Keep it visible and reject any additional error instead of suppressing lint.
legacy_target = [i for i in errors if i.get('id') == 'ExpiredTargetSdkVersion'
                 and any(n.get('file', '').endswith('app/build.gradle.kts') for n in i.findall('location'))]
unexpected = [i for i in errors if i not in legacy_target]
print('Inherited Google Play target-SDK lint issue:', len(legacy_target))
print('Other lint errors:', len(unexpected))
if unexpected:
    for issue in unexpected:
        print(issue.get('id'), issue.get('message'))
    raise SystemExit(1)
PY
