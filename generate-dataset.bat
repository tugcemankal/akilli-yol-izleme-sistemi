@echo off
chcp 65001 >nul
echo ============================================
echo  D400 Sentetik Dataset Ureteci
echo ============================================
echo.

if not exist out (
    echo [HATA] Proje derlenmemis! Once build.bat calistirin.
    pause
    exit /b 1
)

echo Sentetik dataset uretiliyor...
echo.

java -Dfile.encoding=UTF-8 ^
     -cp "out;lib\json-20231013.jar" ^
     main.util.DatasetGenerator

if %ERRORLEVEL% neq 0 (
    echo.
    echo [HATA] Uretim basarisiz.
    pause
    exit /b 1
)

echo.
echo dataset.csv olusturuldu!
echo PC 2'nin d400-classifier\data\ klasorune kopyalayin.
echo.
pause
