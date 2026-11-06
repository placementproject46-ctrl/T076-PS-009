@echo off
setlocal
cd /d "%~dp0"
if exist ".tools\jdk-21.0.13+9\bin\java.exe" (
    set "JAVA_HOME=%~dp0.tools\jdk-21.0.13+9"
    set "PATH=%~dp0.tools\jdk-21.0.13+9\bin;%~dp0.tools\apache-maven-3.9.6\bin;%PATH%"
)

echo Executing JUnit 5 Test Suite for Team T076 / PS-009...
call mvn test "-Dmaven.repo.local=%~dp0.tools\m2-repo"
pause
