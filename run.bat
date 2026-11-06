@echo off
setlocal
cd /d "%~dp0"
if exist ".tools\jdk-21.0.13+9\bin\java.exe" (
    set "JAVA_HOME=%~dp0.tools\jdk-21.0.13+9"
    set "PATH=%~dp0.tools\jdk-21.0.13+9\bin;%~dp0.tools\apache-maven-3.9.6\bin;%PATH%"
)

if not exist "target\stock-brokerage-engine-1.0.0.jar" (
    echo Building project JAR file...
    call mvn clean package -DskipTests
)

echo Starting Stock Brokerage Margin, Settlement and Pledge Engine...
java -jar target\stock-brokerage-engine-1.0.0.jar %*
if "%~1"=="" pause
