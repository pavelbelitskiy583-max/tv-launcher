@echo off
chcp 65001 >nul
REM === Установка лаунчера на телевизор по Wi-Fi ===
REM Положите этот файл, MyTV-Launcher.apk и папку platform-tools рядом.

set /p TVIP=Введите IP телевизора (Настройки - Сеть, например 192.168.1.50):
set ADB=platform-tools\adb.exe

%ADB% connect %TVIP%:5555
echo.
echo Если на экране ТВ появился запрос "Разрешить отладку?" - выберите "Всегда разрешать" и ОК.
pause
%ADB% connect %TVIP%:5555
%ADB% install -r MyTV-Launcher.apk
echo.
echo === Текущий домашний экран (стоковый лаунчер): ===
%ADB% shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
echo.
echo Готово. Нажмите кнопку Home на пульте и выберите "Мой ТВ" - "Всегда".
pause
