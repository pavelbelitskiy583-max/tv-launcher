@echo off
chcp 65001 >nul
REM === Аварийный откат: включает обратно всё из debloat_list.txt и стоковый лаунчер ===
set ADB=platform-tools\adb.exe
for /f "usebackq tokens=* delims=" %%P in ("debloat_list.txt") do %ADB% shell pm enable %%P
if exist stock_launcher.txt for /f "usebackq tokens=* delims=" %%P in ("stock_launcher.txt") do %ADB% shell pm enable %%P
echo Всё включено обратно. Перезагрузите ТВ.
pause
