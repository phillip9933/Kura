param([string]$Serial = 'emulator-5580')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only disposable emulators are permitted.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/sdk" }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$resultDirectory = Join-Path $projectRoot 'test-results'
[IO.Directory]::CreateDirectory($resultDirectory) | Out-Null
& $adb -s $Serial get-state
if ($LASTEXITCODE -ne 0) { throw 'Start the disposable emulator first.' }
$pageSize = (& $adb -s $Serial shell getconf PAGE_SIZE).Trim()
if ($pageSize -ne '16384') { throw "A 16 KiB emulator is required; got $pageSize" }
# UI tests use the known synthetic PIN 2468. Configure it ONLY on a disposable emulator.
& "$projectRoot/gradlew.bat" -p $projectRoot :core:model:test :core:import:testDebugUnitTest :core:import:testLowMemory :core:security:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest :core:import:assembleDebugAndroidTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug --max-workers=1 --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Native build, lint, or JVM tests failed.' }
Copy-Item "$projectRoot/core/model/build/test-results/test/*.xml" $resultDirectory
Copy-Item "$projectRoot/core/import/build/test-results/testDebugUnitTest/*.xml" $resultDirectory
[IO.Directory]::CreateDirectory("$resultDirectory/low-memory") | Out-Null
Copy-Item "$projectRoot/core/import/build/test-results/testLowMemory/*.xml" "$resultDirectory/low-memory"
$tests = @(
    @{ Name='security'; Package='app.kura.prototype.security.test'; Apk='core/security/build/outputs/apk/androidTest/debug/security-debug-androidTest.apk' },
    @{ Name='database'; Package='app.kura.prototype.database.test'; Apk='core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk' },
    @{ Name='import'; Package='app.kura.prototype.importer.test'; Apk='core/import/build/outputs/apk/androidTest/debug/import-debug-androidTest.apk' },
    @{ Name='app'; Package='app.kura.wallet.prototype.test'; Apk='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' }
)
foreach ($test in $tests) {
    if ($test.Name -eq 'app') {
        # Prevent a previous benchmark credential prompt from racing this app.
        & $adb -s $Serial shell am force-stop app.kura.wallet.prototype.benchmark
        & $adb -s $Serial shell am force-stop app.kura.wallet.prototype.profile
        & $adb -s $Serial shell am force-stop app.kura.wallet.prototype
        & $adb -s $Serial shell input keyevent 3
        & $adb -s $Serial install -r "$projectRoot/app/build/outputs/apk/debug/app-debug.apk"
        if ($LASTEXITCODE -ne 0) { throw 'Failed to install prototype app.' }
    }
    & $adb -s $Serial install -r "$projectRoot/$($test.Apk)"
    if ($LASTEXITCODE -ne 0) { throw "Failed to install $($test.Name) tests" }
    $output = & $adb -s $Serial shell am instrument -w "$($test.Package)/androidx.test.runner.AndroidJUnitRunner" 2>&1
    $output | Set-Content "$resultDirectory/$($test.Name)-instrumentation.txt"
    $text = $output -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0 -or $text -notmatch 'OK \(\d+ tests?\)' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "$($test.Name) instrumentation failed; see $resultDirectory"
    }
    $text | Select-String 'OK \(\d+ tests?\)' -AllMatches | ForEach-Object { $_.Matches.Value }
}
"API: $(& $adb -s $Serial shell getprop ro.build.version.sdk); page size: $pageSize; ABI: $(& $adb -s $Serial shell getprop ro.product.cpu.abi)" | Set-Content "$resultDirectory/device.txt"
