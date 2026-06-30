@echo off
chcp 65001 >nul
echo ============================================================
echo  Mersin-Antalya WSN - Akilli Risk Erken Uyari Sistemi
echo  Derleme Scripti
echo ============================================================
echo.

if not exist out mkdir out

echo [1/2] Kaynak dosyalar derleniyor...

set FILES=
for /r src %%f in (*.java) do set FILES=!FILES! "%%f"

setlocal EnableDelayedExpansion
set FILES=
for /r src %%f in (*.java) do set "FILES=!FILES! "%%f""

javac -cp "lib\json-20231013.jar;lib\jfreechart-1.5.4.jar;lib\jcommon-1.0.24.jar" ^
      -d out ^
      -sourcepath src ^
      -encoding UTF-8 ^
      %FILES%

if %ERRORLEVEL% neq 0 (
    echo.
    echo [HATA] Derleme basarisiz! Hata mesajlarini inceleyin.
    pause
    exit /b 1
)

echo [2/2] Derleme BASARILI!
echo.
echo Calistirmak icin: run.bat
echo.
pause
