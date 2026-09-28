param([string]$Serial = 'emulator-5580')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only disposable emulators are permitted.' }
$adb = Join-Path $env:LOCALAPPDATA 'Android/sdk/platform-tools/adb.exe'
$package = 'app.kura.wallet.prototype.benchmark'
$results = Join-Path $PSScriptRoot 'test-results/profile-startup-comparison.txt'
# Requires run-benchmark.ps1 first: the optimized APK must be installed and its
# synthetic vault provisioned through real system authentication.
function Measure-ColdLaunch {
    foreach ($run in 1..3) {
        & $adb -s $Serial shell am force-stop $package
        & $adb -s $Serial shell am start -W -n "$package/app.kura.nativeapp.MainActivity"
        if ($LASTEXITCODE -ne 0) { throw 'Cold launch failed.' }
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
$install = & $adb -s $Serial shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n "$package/androidx.profileinstaller.ProfileInstallReceiver"
$install | Add-Content $results
if (($install -join ' ') -notmatch 'result=1\b') { throw 'Packaged baseline profile installation failed.' }
& $adb -s $Serial shell am force-stop $package
& $adb -s $Serial shell cmd package compile -f -m speed-profile $package
if ($LASTEXITCODE -ne 0) { throw 'Profile compilation failed.' }
'Same R8 APK with packaged baseline profile and speed-profile compilation:' | Add-Content $results
Measure-ColdLaunch | Add-Content $results
& $adb -s $Serial shell dumpsys package $package | Select-String 'status=|compiler-filter|speed-profile' | Add-Content $results
& $adb -s $Serial shell am force-stop $package
Get-Content $results
