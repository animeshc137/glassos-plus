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
