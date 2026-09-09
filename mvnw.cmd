@echo off
setlocal
set BASE_DIR=%~dp0
set MAVEN_VERSION=3.9.11
set WRAPPER_DIR=%BASE_DIR%.mvn\wrapper
set MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
set ARCHIVE=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.tar.gz
set URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.tar.gz

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%ARCHIVE%" powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%ARCHIVE%'"
  tar -xzf "%ARCHIVE%" -C "%WRAPPER_DIR%"
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
endlocal
