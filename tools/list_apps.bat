@echo off
chcp 65001 >nul
REM === Сохраняет список всех пакетов в packages.txt, чтобы выбрать, что отключить ===
set ADB=platform-tools\adb.exe
echo --- Установленные пользователем / продавцом --- > packages.txt
%ADB% shell pm list packages -3 >> packages.txt
echo. >> packages.txt
echo --- Системные --- >> packages.txt
%ADB% shell pm list packages -s >> packages.txt
echo Список сохранён в packages.txt
pause
