param([string]$Serial = 'emulator-5580')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only disposable emulators are permitted.' }
$projectRoot = $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android/sdk/platform-tools/adb.exe'
if ((& $adb -s $Serial shell getconf PAGE_SIZE).Trim() -ne '16384') { throw 'A 16 KiB emulator is required.' }
& "$projectRoot/gradlew.bat" -p $projectRoot :benchmark:assembleDebugAndroidTest :app:assembleBenchmark --max-workers=1
if ($LASTEXITCODE -ne 0) { throw 'Benchmark build failed.' }
$results = Join-Path $projectRoot 'test-results'
[IO.Directory]::CreateDirectory($results) | Out-Null
& $adb -s $Serial install -r "$projectRoot/app/build/outputs/apk/benchmark/app-benchmark.apk"
if ($LASTEXITCODE -ne 0) { throw 'Benchmark app install failed.' }
& $adb -s $Serial install -r "$projectRoot/benchmark/build/outputs/apk/androidTest/debug/benchmark-debug-androidTest.apk"
if ($LASTEXITCODE -ne 0) { throw 'Benchmark test install failed.' }
& $adb -s $Serial shell am force-stop app.kura.wallet.prototype
& $adb -s $Serial shell am force-stop app.kura.wallet.prototype.benchmark
        & $adb -s $Serial shell am force-stop app.kura.wallet.prototype.profile
& $adb -s $Serial shell input keyevent 3
$output = & $adb -s $Serial shell am instrument -w app.kura.prototype.benchmarktest/androidx.test.runner.AndroidJUnitRunner 2>&1
$output | Set-Content "$results/benchmark-instrumentation.txt"
$text = $output -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0 -or $text -notmatch 'OK \(\d+ tests?\)' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw 'Optimized journey failed.' }
$cold = foreach ($run in 1..3) {
    & $adb -s $Serial shell am force-stop app.kura.wallet.prototype.benchmark
        & $adb -s $Serial shell am force-stop app.kura.wallet.prototype.profile
    & $adb -s $Serial shell am start -W -n app.kura.wallet.prototype.benchmark/app.kura.nativeapp.MainActivity
}
$cold | Set-Content "$results/benchmark-startup.txt"
& $adb -s $Serial shell am force-stop app.kura.wallet.prototype.benchmark
        & $adb -s $Serial shell am force-stop app.kura.wallet.prototype.profile
Write-Output 'Optimized journey passed; measurements are emulator observations, not hardware guarantees.'
