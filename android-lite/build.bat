@echo off
setlocal

set ANDROID_HOME=C:\Android\sdk
set BUILD_TOOLS=%ANDROID_HOME%\build-tools\34.0.0
set PLATFORM=%ANDROID_HOME%\platforms\android-33\android.jar

set PROJECT=c:\Users\user\Pictures\dataapp\android-lite
set SRC=%PROJECT%\src
set RES=%PROJECT%\res
set GEN=%PROJECT%\gen
set BIN=%PROJECT%\bin
set OUT=%PROJECT%\out

echo === DataSaver APK Builder ===
echo.

echo [1/7] Cleaning...
if exist "%GEN%" rmdir /s /q "%GEN%"
if exist "%BIN%" rmdir /s /q "%BIN%"
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%GEN%"
mkdir "%BIN%"
mkdir "%BIN%\classes"
mkdir "%OUT%"

echo [2/7] Generating R.java...
"%BUILD_TOOLS%\aapt.exe" package -f -m -S "%RES%" -J "%GEN%" -M "%PROJECT%\AndroidManifest.xml" -I "%PLATFORM%"
if errorlevel 1 (
    echo ERROR: aapt failed
    exit /b 1
)

echo [3/7] Compiling Java...
javac --release 11 -classpath "%PLATFORM%" -d "%BIN%\classes" "%SRC%\com\datasaver\MainActivity.java" "%SRC%\com\datasaver\DataSaverService.java" "%SRC%\com\datasaver\DataSaverVpnService.java" "%GEN%\com\datasaver\R.java"
if errorlevel 1 (
    echo ERROR: javac failed
    exit /b 1
)

echo [4/7] Converting to DEX...
setlocal enabledelayedexpansion
set CLASSFILES=
for %%f in ("%BIN%\classes\com\datasaver\*.class") do set CLASSFILES=!CLASSFILES! "%%f"
endlocal & set CLASSFILES=%CLASSFILES%
call "%BUILD_TOOLS%\d8.bat" --release --output "%BIN%" --lib "%PLATFORM%" %CLASSFILES%
if errorlevel 1 (
    echo ERROR: d8 failed
    exit /b 1
)

echo [5/7] Packaging APK...
"%BUILD_TOOLS%\aapt.exe" package -f -S "%RES%" -M "%PROJECT%\AndroidManifest.xml" -I "%PLATFORM%" -F "%OUT%\datasaver-unsigned.apk"
if errorlevel 1 (
    echo ERROR: aapt package failed
    exit /b 1
)

echo [5b] Adding DEX to APK...
cd "%BIN%"
"%BUILD_TOOLS%\aapt.exe" add "%OUT%\datasaver-unsigned.apk" classes.dex
if errorlevel 1 (
    echo ERROR: adding dex failed
    exit /b 1
)
cd "%PROJECT%"

echo [6/7] Aligning APK...
"%BUILD_TOOLS%\zipalign.exe" -f 4 "%OUT%\datasaver-unsigned.apk" "%OUT%\datasaver-aligned.apk"
if errorlevel 1 (
    echo ERROR: zipalign failed
    exit /b 1
)

echo [7/7] Signing APK...
if not exist "%PROJECT%\debug.keystore" (
    keytool -genkeypair -v -keystore "%PROJECT%\debug.keystore" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug, OU=Debug, O=Debug, L=Debug, ST=Debug, C=US"
)

call "%BUILD_TOOLS%\apksigner.bat" sign --ks "%PROJECT%\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "%OUT%\datasaver.apk" "%OUT%\datasaver-aligned.apk"
if errorlevel 1 (
    echo ERROR: signing failed
    exit /b 1
)

echo.
echo ========================================
echo   BUILD SUCCESSFUL!
echo   APK: %OUT%\datasaver.apk
echo ========================================
echo.
echo To install on phone (USB):
echo   adb install "%OUT%\datasaver.apk"
echo.
