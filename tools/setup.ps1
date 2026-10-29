# ================================================================
#  Настройка телевизора Starwind: чистка мусора + установка приложений
# ================================================================
$ErrorActionPreference = 'Continue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Set-Location -LiteralPath $PSScriptRoot

$TV  = '192.168.3.22'
$DEV = "${TV}:5555"
$ADB = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
$DL  = Join-Path $PSScriptRoot 'downloads'
New-Item -ItemType Directory -Force -Path $DL | Out-Null

function Step($t) { Write-Host ''; Write-Host "== $t ==" -ForegroundColor Cyan }
function Ok($t)   { Write-Host "  OK  $t" -ForegroundColor Green }
function Warn($t) { Write-Host "  !!  $t" -ForegroundColor Yellow }
function A { & $ADB -s $DEV @args 2>&1 }
function Get-File($url, $out) {
    try { Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $out; return $true }
    catch { Warn "Не скачалось: $url"; return $false }
}
function Install-Apk($path, $name) {
    Write-Host "  ...  ставлю $name"
    $r = A install -r $path | Out-String
    if ($r -match 'Success') { Ok $name } else { Warn "$name : $($r.Trim())" }
}

# ---------- 1. ADB ----------
Step '1. Инструменты'
if (-not (Test-Path $ADB)) {
    Write-Host '  Скачиваю Platform Tools...'
    $zip = Join-Path $DL 'pt.zip'
    if (Get-File 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' $zip) {
        Expand-Archive -Force $zip $PSScriptRoot
    }
}
if (-not (Test-Path $ADB)) { Warn 'Нет adb. Скачайте platform-tools вручную.'; Read-Host 'Enter'; exit 1 }
Ok 'adb готов'

# ---------- 2. Подключение ----------
Step "2. Подключение к ТВ $TV"
& $ADB kill-server 2>&1 | Out-Null
& $ADB connect $DEV | Out-Host
Write-Host ''
Write-Host '  Если на ТВ появилось "Разрешить отладку?" — отметьте "Всегда" и нажмите ОК.' -ForegroundColor Yellow
Read-Host '  Потом нажмите Enter здесь'
& $ADB connect $DEV | Out-Null
$state = (A get-state | Out-String).Trim()
if ($state -ne 'device') {
    Warn "ТВ не отвечает ($state). Проверьте: ТВ включён, та же Wi-Fi сеть, отладка включена."
    Read-Host 'Enter'; exit 1
}
$sdk = [int]((A shell getprop ro.build.version.sdk | Out-String).Trim())
$ver = (A shell getprop ro.build.version.release | Out-String).Trim()
$abi = (A shell getprop ro.product.cpu.abi | Out-String).Trim()
Ok "Android $ver (API $sdk), процессор $abi"

# ---------- 3. Лаунчер ----------
Step '3. Лаунчер «Мой ТВ»'
$launcher = Get-ChildItem -Path $PSScriptRoot -Filter 'MyTV-Launcher*.apk' | Select-Object -First 1
if ($launcher) { Install-Apk $launcher.FullName 'Мой ТВ' } else { Warn 'MyTV-Launcher.apk не найден рядом со скриптом — пропускаю' }

# ---------- 4. Чистка ----------
Step '4. Отключение ненужных приложений'
$junk = @(
    '^ru\.ivi\.', 'karaoke', 'eshare', 'imirror', 'weather', 'slideshow', 'memmanager',
    'abox.*(discovery|search)', 'ohdexplorer', 'okko', 'megogo', '\.wink', 'cleaner', 'booster',
    'appmarket', 'appstore\.(?!vk)', 'advert', '\.ads\.', 'analytics'
)
$protect = 'settings|inputmethod|keyboard|leanbackime|packageinstaller|systemui|webview|\.gms|gsf|vending|providers|tvplayer|livetv|inputsource|tvinput|mstar|happ|ru\.mytv|license|rustore|vk\.store|kinopoisk|yandex|youtube|smarttube|netflix|apple|instagram'
$all = (A shell pm list packages | Out-String) -split "`r?`n" | ForEach-Object { $_ -replace '^package:', '' } | Where-Object { $_ }
$found = $all | Where-Object { $p = $_; ($junk | Where-Object { $p -match $_ }) -and ($p -notmatch $protect) }
if (-not $found) { Ok 'Мусор не найден' }
else {
    Write-Host '  Будут отключены (обратимо):'
    $found | ForEach-Object { Write-Host "     $_" }
    $ans = Read-Host '  Отключить? (y = да, n = нет)'
    if ($ans -match '^[yдYД]') {
        foreach ($p in $found) {
            $r = A shell pm disable-user --user 0 $p | Out-String
            A shell am force-stop $p | Out-Null
            if ($r -match 'disabled') { Ok $p; Add-Content -Path 'disabled_packages.txt' -Value $p }
            else { Warn "$p : $($r.Trim())" }
        }
    }
}
Write-Host ''
Write-Host '  Полный список пакетов сохранён в packages.txt — если хотите отключить что-то ещё,' -ForegroundColor DarkGray
Write-Host '  выполните:  platform-tools\adb.exe shell pm disable-user --user 0 ИМЯ.ПАКЕТА' -ForegroundColor DarkGray
$all | Set-Content 'packages.txt'

# ---------- 5. Приложения ----------
Step '5. Установка приложений'

# SmartTube — YouTube без рекламы для ТВ
try {
    $rel = Invoke-RestMethod -UseBasicParsing 'https://api.github.com/repos/yuliskov/SmartTube/releases/latest'
    $apks = $rel.assets | Where-Object { $_.name -like '*.apk' }
    $pick = $apks | Where-Object { $_.name -match 'stable' -and $_.name -match [regex]::Escape($abi) } | Select-Object -First 1
    if (-not $pick) { $pick = $apks | Where-Object { $_.name -match 'stable' -and $_.name -match 'armeabi-v7a' } | Select-Object -First 1 }
    if (-not $pick) { $pick = $apks | Where-Object { $_.name -match 'stable' } | Select-Object -First 1 }
    if (-not $pick) { $pick = $apks | Select-Object -First 1 }
    $f = Join-Path $DL 'SmartTube.apk'
    if ($pick -and (Get-File $pick.browser_download_url $f)) { Install-Apk $f "YouTube (SmartTube $($rel.tag_name))" }
} catch { Warn 'SmartTube: не удалось получить релиз с GitHub' }

# RuStore для ТВ — оттуда ставятся Кинопоиск, Яндекс Браузер и др.
if ($sdk -ge 26) {
    $f = Join-Path $DL 'RuStoreTV.apk'
    if (Get-File 'https://static.rustore.ru/release/RuStoreTV.apk' $f) { Install-Apk $f 'RuStore для ТВ' }
} else {
    Warn "RuStore требует Android 8+, а у вас Android $ver. Кинопоиск и Яндекс Браузер положите APK-файлами в папку apk."
}

# Всё, что лежит в папке apk (Happ, Кинопоиск и т.д.)
$extra = Join-Path $PSScriptRoot 'apk'
if (Test-Path $extra) {
    Get-ChildItem $extra -Filter *.apk | ForEach-Object { Install-Apk $_.FullName $_.BaseName }
}

# ---------- 6. Главный экран ----------
Step '6. «Мой ТВ» — главный экран'
A shell cmd package set-home-activity ru.mytv.launcher/.MainActivity | Out-Null
A shell am start -n ru.mytv.launcher/.MainActivity | Out-Null
Ok 'Если при нажатии Home спросят — выберите «Мой ТВ» → Всегда'

Write-Host ''
Write-Host '================ ГОТОВО ================' -ForegroundColor Green
Write-Host ' Откат отключённых приложений: restore.bat'
Read-Host 'Enter — закрыть'
