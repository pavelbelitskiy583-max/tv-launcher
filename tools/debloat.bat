@echo off
chcp 65001 >nul
REM =====================================================================
REM  Отключение мусора. Это ОБРАТИМО: пакеты не удаляются, а "замораживаются".
REM  Вернуть любой:  platform-tools\adb.exe shell pm enable <пакет>
REM
REM  Впишите в debloat_list.txt пакеты из packages.txt (по одному в строке,
REM  без "package:"). НЕ отключайте:
REM    - настройки (*.settings, *tv.settings)
REM    - приложение ТВ-тюнера / HDMI-входов (часто mstar, tvplayer, livetv, inputsource)
REM    - клавиатуру (inputmethod, latin, leanbackime)
REM    - packageinstaller, systemui, webview, gms, providers
REM    - сам Happ и наш лаунчер (ru.mytv.launcher)
REM =====================================================================
set ADB=platform-tools\adb.exe
for /f "usebackq tokens=* delims=" %%P in ("debloat_list.txt") do (
  echo Отключаю %%P
  %ADB% shell pm disable-user --user 0 %%P
  %ADB% shell am force-stop %%P
)
echo.
echo Готово. Перезагрузите телевизор.
pause
