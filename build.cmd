@echo off
rem Builds the plugin jar into build\libs. Uses the portable JDK in .tools if present.
if exist "%~dp0.tools\jdk-25\bin\java.exe" set "JAVA_HOME=%~dp0.tools\jdk-25"
call "%~dp0gradlew.bat" build %*
