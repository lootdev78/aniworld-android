#!/bin/sh

# Gradle start up script for AniWorldAndroid.
# Wrapper properties are configured for Gradle 8.14.3.

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
  JAVA_EXE="${JAVA_HOME:-}/bin/java"
  if [ ! -x "$JAVA_EXE" ]; then
    JAVA_EXE="java"
  fi
  exec "$JAVA_EXE" -Xmx64m -Xms64m $JAVA_OPTS $GRADLE_OPTS \
    -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  echo "gradle-wrapper.jar fehlt; verwende systemweite Gradle-Installation." >&2
  exec gradle "$@"
fi

echo "gradle-wrapper.jar fehlt." >&2
echo "Erzeuge sie lokal mit: gradle wrapper --gradle-version 8.14.3" >&2
echo "Danach: ./gradlew assembleDebug" >&2
exit 1
