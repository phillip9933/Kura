param([string]$Serial = 'emulator-5580', [switch]$Unlock)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only disposable emulators are permitted.' }
$adb = Join-Path $env:LOCALAPPDATA 'Android/sdk/platform-tools/adb.exe'
if ((& $adb -s $Serial shell getconf PAGE_SIZE).Trim() -ne '16384') { throw 'A 16 KiB emulator is required.' }
$package = 'app.kura.wallet.prototype.benchmark'
$results = Join-Path $projectRoot 'test-results/profile-startup-comparison.txt'
# Requires run-benchmark.ps1 first: the optimized APK must be installed and its
# synthetic vault provisioned through real system authentication.
function Measure-ColdLaunch {
    foreach ($run in 1..3) {
        & $adb -s $Serial shell am force-stop $package
        & $adb -s $Serial shell am start -W -n "$package/app.kura.nativeapp.MainActivity"
        if ($LASTEXITCODE -ne 0) { throw 'Cold launch failed.' }
    }
}
function Measure-AuthenticatedUnlock([string]$Label) {
    & $adb -s $Serial shell am force-stop $package
    & $adb -s $Serial shell atrace --async_start -b 32768 -a $package view am
    if ($LASTEXITCODE -ne 0) { throw 'Trace start failed.' }
    try {
        $output = & $adb -s $Serial shell am instrument -w -e class app.kura.benchmark.UnlockTimingTest -e unlockTiming true -e targetPackage $package app.kura.prototype.benchmarktest/androidx.test.runner.AndroidJUnitRunner
        $output | Set-Content (Join-Path $projectRoot "test-results/profile-$Label-unlock.txt")
        $text = $output -join ' '
        if ($LASTEXITCODE -ne 0 -or $text -notmatch 'OK \(1 test\)' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw 'Unlock measurement failed.' }
    } finally {
        & $adb -s $Serial shell atrace --async_stop > (Join-Path $projectRoot "test-results/profile-$Label-unlock.atrace")
    }
}
& $adb -s $Serial shell am force-stop app.kura.wallet.prototype
& $adb -s $Serial shell am force-stop app.kura.wallet.prototype.profile
& $adb -s $Serial shell am broadcast -a androidx.profileinstaller.action.SKIP_FILE --es EXTRA_SKIP_FILE_OPERATION WRITE_SKIP_FILE -n "$package/androidx.profileinstaller.ProfileInstallReceiver"
& $adb -s $Serial shell am force-stop $package
& $adb -s $Serial shell cmd package compile -f -m verify $package
if ($LASTEXITCODE -ne 0) { throw 'Compilation reset failed.' }
& $adb -s $Serial shell pm art clear-app-profiles $package
if ($LASTEXITCODE -ne 0) { throw 'Profile clearing failed.' }
'R8 APK with verify compilation and cleared profiles (three cold launches):' | Set-Content $results
Measure-ColdLaunch | Add-Content $results
if ($Unlock) { Measure-AuthenticatedUnlock 'verify' }
$install = & $adb -s $Serial shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n "$package/androidx.profileinstaller.ProfileInstallReceiver"
$install | Add-Content $results
if (($install -join ' ') -notmatch 'result=1\b') { throw 'Packaged baseline profile installation failed.' }
& $adb -s $Serial shell am force-stop $package
& $adb -s $Serial shell cmd package compile -f -m speed-profile $package
if ($LASTEXITCODE -ne 0) { throw 'Profile compilation failed.' }
'Same R8 APK with packaged baseline profile and speed-profile compilation:' | Add-Content $results
Measure-ColdLaunch | Add-Content $results
if ($Unlock) { Measure-AuthenticatedUnlock 'compiled' }
& $adb -s $Serial shell dumpsys package $package | Select-String 'status=|compiler-filter|speed-profile' | Add-Content $results
& $adb -s $Serial shell am force-stop $package
Get-Content $results
