@echo off
chcp 65001 >nul
echo ============================================================
echo  Mersin-Antalya WSN - Akilli Risk Erken Uyari Sistemi
echo  Calistirma Scripti
echo ============================================================
echo.
echo Uygulama baslatiliyor...
echo Tarayicinizda harita otomatik acilacak: http://localhost:8765/map
echo.

java -Dfile.encoding=UTF-8 -cp "out;lib\json-20231013.jar;lib\jfreechart-1.5.4.jar;lib\jcommon-1.0.24.jar" main.Main

if %ERRORLEVEL% neq 0 (
    echo.
    echo [HATA] Uygulama calistirilemiyor.
    echo Lutfen once build.bat ile derleyin.
    pause
)
