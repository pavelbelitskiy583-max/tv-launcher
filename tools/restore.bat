@echo off
chcp 65001 >nul
cd /d "%~dp0"
set ADB=platform-tools\adb.exe
%ADB% connect 192.168.3.22:5555
if not exist disabled_packages.txt (echo Нечего восстанавливать & pause & exit /b)
for /f "usebackq tokens=* delims=" %%P in ("disabled_packages.txt") do (
  echo Включаю %%P
  %ADB% -s 192.168.3.22:5555 shell pm enable %%P
)
del disabled_packages.txt
echo Готово. Перезагрузите ТВ.
pause
