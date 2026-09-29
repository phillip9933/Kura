param([string]$Serial = 'emulator-5580')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only disposable emulators are permitted.' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/sdk" }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
function Invoke-Adb {
    & $adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $args" }
}
if ((Invoke-Adb shell getconf PAGE_SIZE).Trim() -ne '16384') { throw 'Use the disposable 16 KiB emulator.' }
$oldSize = (Invoke-Adb shell wm size) -join "`n"
$oldDensity = (Invoke-Adb shell wm density) -join "`n"
$restoreSize = if ($oldSize -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$restoreDensity = if ($oldDensity -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
$results = Join-Path $projectRoot 'verification'
[IO.Directory]::CreateDirectory($results) | Out-Null
try {
    # A real 9:16 emulator viewport avoids cropping or fabricating store screenshots.
    Invoke-Adb shell wm size 1080x1920
    Invoke-Adb shell wm density 360
    Invoke-Adb shell am force-stop app.kura.wallet.prototype.benchmark
    Invoke-Adb shell am force-stop app.kura.wallet.prototype.profile
    Invoke-Adb shell am force-stop app.kura.wallet.prototype
    Invoke-Adb shell input keyevent 3
    Invoke-Adb install -r "$projectRoot/app/build/outputs/apk/debug/app-debug.apk"
    Invoke-Adb install -r "$projectRoot/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    $output = Invoke-Adb shell am instrument -w -e class app.kura.nativeapp.StoreScreenshotsTest -e storeScreenshots true app.kura.wallet.prototype.test/androidx.test.runner.AndroidJUnitRunner
    $output | Set-Content "$results/store-captures-tests.txt"
    $text = $output -join "`n"
    if ($text -notmatch 'OK \(1 test\)' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw 'Capture failed; inspect verification/store-captures-tests.txt'
    }
    $destination = "$projectRoot/fastlane/metadata/android/en-US/images/phoneScreenshots"
    [IO.Directory]::CreateDirectory($destination) | Out-Null
    foreach ($name in @('1-passes-dark', '2-boarding-pass', '3-fullscreen-barcode', '4-cards-dark', '5-cards-light')) {
        Invoke-Adb pull "/sdcard/Android/data/app.kura.wallet.prototype/files/store-screenshots/$name.jpg" "$destination/$name.jpg"
    }
    'Captured five fictional-data screenshots. Inspect them before publication.'
} finally {
    Invoke-Adb shell wm size $restoreSize
    Invoke-Adb shell wm density $restoreDensity
}
