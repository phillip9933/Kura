param([string]$Serial = 'emulator-5580')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only disposable emulators are permitted.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android/sdk/platform-tools/adb.exe'
$package = 'app.kura.wallet.prototype.profile'
if ([int](& $adb -s $Serial shell getprop ro.build.version.sdk) -lt 34) { throw 'Profile capture requires API 34 or later.' }
& "$projectRoot/gradlew.bat" -p $projectRoot :app:assembleProfile :benchmark:assembleDebugAndroidTest --max-workers=1
if ($LASTEXITCODE -ne 0) { throw 'Profile build failed.' }
& $adb -s $Serial install -r "$projectRoot/app/build/outputs/apk/profile/app-profile.apk"
if ($LASTEXITCODE -ne 0) { throw 'Profile install failed.' }
& $adb -s $Serial install -r "$projectRoot/benchmark/build/outputs/apk/androidTest/debug/benchmark-debug-androidTest.apk"
if ($LASTEXITCODE -ne 0) { throw 'Journey install failed.' }
foreach ($identity in @('app.kura.wallet.prototype','app.kura.wallet.prototype.benchmark',$package)) {
    & $adb -s $Serial shell am force-stop $identity
}
& $adb -s $Serial shell input keyevent 3
& $adb -s $Serial shell am broadcast -a androidx.profileinstaller.action.SKIP_FILE --es EXTRA_SKIP_FILE_OPERATION WRITE_SKIP_FILE -n "$package/androidx.profileinstaller.ProfileInstallReceiver"
& $adb -s $Serial shell am force-stop $package
& $adb -s $Serial shell cmd package compile -f -m verify $package
if ($LASTEXITCODE -ne 0) { throw 'Compilation reset failed.' }
& $adb -s $Serial shell pm art clear-app-profiles $package
if ($LASTEXITCODE -ne 0) { throw 'Profile clearing failed.' }
$results = Join-Path $projectRoot 'test-results'
[IO.Directory]::CreateDirectory($results) | Out-Null
$output = & $adb -s $Serial shell am instrument -w -e targetPackage $package app.kura.prototype.benchmarktest/androidx.test.runner.AndroidJUnitRunner
$output | Set-Content "$results/profile-journey.txt"
$text = $output -join [Environment]::NewLine
if ($text -notmatch 'OK \(1 test\)' -or $text -match 'FAILURES!!!|Process crashed') { throw 'Capture journey failed.' }
Start-Sleep -Seconds 6
& $adb -s $Serial shell am broadcast -a androidx.profileinstaller.action.SAVE_PROFILE -n "$package/androidx.profileinstaller.ProfileInstallReceiver"
Start-Sleep -Seconds 2
& $adb -s $Serial shell am force-stop $package
& $adb -s $Serial shell pm dump-profiles --dump-classes-and-methods $package
if ($LASTEXITCODE -ne 0) { throw 'Profile dump failed.' }
& $adb -s $Serial pull "/data/misc/profman/$package-primary.prof.txt" "$results/captured-baseline-prof.txt"
if ($LASTEXITCODE -ne 0) { throw 'Profile retrieval failed.' }
$rules = Get-Content "$results/captured-baseline-prof.txt" | Where-Object { $_ -match '^[HSP]*L' } | Sort-Object -Unique
if (!($rules | Where-Object { $_ -match 'Lapp/kura/' })) { throw 'No Kura methods were captured.' }
[IO.File]::WriteAllLines((Join-Path $projectRoot 'app/src/main/baseline-prof.txt'), [string[]]$rules, [Text.UTF8Encoding]::new($false))
"Captured $($rules.Count) measured profile rules."
