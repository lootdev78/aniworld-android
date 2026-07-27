@rem Gradle startup script for Windows generated for AniWorldAndroid.
@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" goto runWrapper
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  echo gradle-wrapper.jar fehlt; verwende systemweite Gradle-Installation. 1>&2
  gradle %*
  exit /b %ERRORLEVEL%
)
echo gradle-wrapper.jar fehlt. 1>&2
echo Erzeuge sie lokal mit: gradle wrapper --gradle-version 8.14.3 1>&2
echo Danach: gradlew assembleDebug 1>&2
exit /b 1
:runWrapper
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -Xmx64m -Xms64m %JAVA_OPTS% %GRADLE_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
