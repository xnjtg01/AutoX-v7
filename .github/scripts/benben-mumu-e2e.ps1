# MuMu local Android end-to-end harness. Run ONLY on a dedicated Windows
# self-hosted runner, never on untrusted pull requests.
# Screenshots remain on Windows; only the seven result classifications enter CI logs.
param([Parameter(Mandatory=$true)][string]$ApkPath)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$adb = if ($env:BENBEN_ADB_PATH) { $env:BENBEN_ADB_PATH } else { 'adb' }
if (-not (Get-Command $adb -ErrorAction SilentlyContinue)) {
  throw 'ADB unavailable; install Android platform-tools or set BENBEN_ADB_PATH'
}
$game = if ($env:BENBEN_GAME_PACKAGE) { $env:BENBEN_GAME_PACKAGE } else { 'com.got.china' }
$pkg = 'com.xnjtg.benben'
$root = "/sdcard/Android/data/$pkg/files/diagnostic-sessions"
$raw = & $adb devices
$devices = @($raw | Select-String -Pattern '^([^\s]+)\s+device$' |
  ForEach-Object {$_.Matches.Groups[1].Value})
if ($env:BENBEN_ADB_SERIAL) {
  if ($devices -notcontains $env:BENBEN_ADB_SERIAL) {
    throw "Specified BENBEN_ADB_SERIAL not connected: $env:BENBEN_ADB_SERIAL"
  }
  $serial = $env:BENBEN_ADB_SERIAL
} else {
  if ($devices.Count -ne 1) {
    throw "Expected one ADB emulator; specify BENBEN_ADB_SERIAL. Found $($devices.Count)"
  }
  $serial = $devices[0]
}
Write-Host "MuMu test target verified. Serial: $serial"
$installed = (& $adb -s $serial shell pm path $game | Out-String)
if ($installed -notmatch 'package:') {
  throw "Game $game not installed; specify BENBEN_GAME_PACKAGE for the domestic APK"
}
$size = (& $adb -s $serial shell wm size | Out-String)
Write-Host "Screen: $size"
if ($size -notmatch '720x1280') {
  throw 'E2E requires 720x1280; refusing unknown coordinate mapping.'
}
$existing = @(& $adb -s $serial shell ls -1 $root 2>$null |
  Where-Object {$_ -match '^session-'})
& $adb -s $serial install -r $ApkPath
if ($LASTEXITCODE -ne 0) {throw 'APK installation failed'}
& $adb -s $serial shell am force-stop $pkg
& $adb -s $serial shell am start -W -n "$pkg/.MainActivity" -a 'com.xnjtg.benben.RUN_LOCAL_E2E'
if ($LASTEXITCODE -ne 0) {throw 'Cannot start explicit debug E2E intent'}
Start-Sleep -Seconds 1
& $adb -s $serial shell monkey -p $game -c android.intent.category.LAUNCHER 1
if ($LASTEXITCODE -ne 0) {throw 'Cannot switch to the running game'}
Write-Host 'Game launched. Running seven isolated scene checks without user taps.'
$session = $null
for ($i=0;$i -lt 78;$i++) {
  Start-Sleep -Seconds 10
  $folders = @(& $adb -s $serial shell ls -1 $root 2>$null |
    Where-Object {$_ -match '^session-'})
  $created = @($folders | Where-Object {$existing -notcontains $_})
  if ($created.Count -eq 0) {continue}
  $session = $created[-1].Trim()
  $target = "$root/$session/batch-outcomes.txt"
  & $adb -s $serial shell ls $target *> $null
  if ($LASTEXITCODE -eq 0) {break}
  $session = $null
}
if (-not $session) {throw 'No batch-outcomes.txt generated; check device diagnostics locally'}
$folder = Join-Path $env:RUNNER_TEMP 'benben-local-test'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$summaryPath = Join-Path $folder 'batch-outcomes.txt'
& $adb -s $serial pull "$root/$session/batch-outcomes.txt" $summaryPath | Out-Null
if ($LASTEXITCODE -ne 0) {throw 'Cannot pull task summary. Full screenshots remain private.'}
$summary = Get-Content -Path $summaryPath -Encoding UTF8
$rows = @($summary | Where-Object {$_ -match '^(邮件奖励|每日任务|联盟帮助|训练士兵|加入集结|资源采集|自动打野怪)='})
Write-Host "Seven-scene result count: $($rows.Count)"
foreach ($row in $rows) {
  if ($row -match '^([^=]+)=(ACTION|SKIP|FAIL|BLOCKED)') {
    Write-Host "$($Matches[1]) = $($Matches[2])"
  }
}
if ($rows.Count -ne 7) {throw 'Regression: seven independent outcomes were not recorded'}
if (@($rows | Where-Object {$_ -match '=BLOCKED' }).Count -gt 0) {
  throw 'E2E has blocked tasks; inspect game screenshots LOCALLY on the runner'
}
Write-Host 'E2E passed the no-blocked check. ACTION does not prove gameplay success.'
