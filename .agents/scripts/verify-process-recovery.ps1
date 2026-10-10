param(
    [string]$Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"
$packageName = "com.fifa.ocr"
$activityName = "$packageName/.MainActivity"
$serviceName = "$packageName/.RecoveryProbeService"
$failurePoints = @(
    "AFTER_PENDING_INSERT",
    "AFTER_TEMP_WRITE",
    "AFTER_VALIDATION",
    "AFTER_RENAME_BEFORE_DB_COMMIT",
    "AFTER_DB_COMMIT"
)

function Invoke-Adb([string[]]$Arguments) {
    $output = & adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed ($LASTEXITCODE): $($Arguments -join ' ')`n$output"
    }
    return ($output -join "`n").Trim()
}

function Get-AppPid {
    $output = & adb -s $Serial shell pidof $packageName 2>$null
    if ($LASTEXITCODE -ne 0) { return "" }
    return ($output -join "").Trim()
}

function Wait-For([scriptblock]$Condition, [string]$Description, [int]$Seconds = 40) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (& $Condition) { return }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for $Description"
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is not available on PATH"
}
if ((Invoke-Adb @("get-state")) -ne "device") {
    throw "Device $Serial is not ready"
}

& .\gradlew.bat :app:assembleDebug --no-configuration-cache
if ($LASTEXITCODE -ne 0) { throw "Debug APK assembly failed" }
Invoke-Adb @("install", "-r", "app/build/outputs/apk/debug/app-debug.apk") | Out-Null

foreach ($point in $failurePoints) {
    Invoke-Adb @("shell", "am", "force-stop", $packageName) | Out-Null
    Invoke-Adb @("shell", "am", "start", "-S", "-n", $activityName) | Out-Null
    Wait-For { (Get-AppPid) -ne "" } "app startup before $point"

    & adb -s $Serial shell am startservice -n $serviceName --es failure_point $point | Out-Null
    Wait-For { (Get-AppPid) -eq "" } "process death at $point"

    Invoke-Adb @("shell", "am", "start", "-S", "-n", $activityName) | Out-Null
    Wait-For { (Get-AppPid) -ne "" } "app restart after $point"
    Invoke-Adb @("shell", "am", "startservice", "-n", $serviceName, "--ez", "verify", "true") | Out-Null
    $verified = $false
    for ($i = 0; $i -lt 40; $i++) {
        $marker = & adb -s $Serial shell run-as $packageName cat files/recovery-probe.properties 2>$null
        $markerText = $marker -join "`n"
        if ($markerText -match "(?m)^verified=true\s*$") {
            $verified = $true
            break
        }
        if ($markerText -match "(?m)^verified=false\s*$") {
            throw "Reconciliation verification failed after $point`n$markerText"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $verified) { throw "Timed out waiting for reconciliation verification after $point" }

    $marker = Invoke-Adb @("shell", "run-as", $packageName, "cat", "files/recovery-probe.properties")
    if ($marker -notmatch "(?m)^failurePoint=$([regex]::Escape($point))\s*$") {
        throw "Probe marker does not match $point`n$marker"
    }
    Write-Host "${point}: verified"
}

Write-Host "All process recovery failure points passed on $Serial."
