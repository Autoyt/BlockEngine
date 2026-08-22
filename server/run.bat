@echo off
setlocal

set "SERVER_JAR="

for %%F in (server.jar paper-*.jar *.jar) do (
    if not defined SERVER_JAR if exist "%%F" set "SERVER_JAR=%%F"
)

if not defined SERVER_JAR (
    echo No Paper server jar was found in %CD%.
    echo Place one in this folder as server.jar or paper-*.jar, then run again.
    pause
    exit /b 1
)

java -Xms2G -Xmx2G -jar "%SERVER_JAR%" --nogui
