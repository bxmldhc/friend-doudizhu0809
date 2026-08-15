param(
    [string]$VersionName = "1.2.0",
    [int]$VersionCode = 3,
    [string[]]$ReleaseNotes = @(
        "Password confirmation and small-screen scrolling",
        "Clickable room invitations with direct app entry",
        "In-game voice toggle, top landlord cards, and player stats"
    ),
    [switch]$ForceUpdate
)

$ErrorActionPreference = "Stop"
if ($VersionCode -lt 1) {
    throw "VersionCode must be greater than zero."
}
if ([string]::IsNullOrWhiteSpace($VersionName)) {
    throw "VersionName cannot be empty."
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkSource = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"
$releaseDir = Join-Path $projectRoot "server-deno\releases"
$apkName = "friend-doudizhu-$VersionName.apk"
$apkTarget = Join-Path $releaseDir $apkName
$manifestTarget = Join-Path $releaseDir "latest.json"

if (-not (Test-Path $apkSource)) {
    throw "Signed release APK not found. Run .\gradlew.bat assembleRelease first."
}

New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
Copy-Item -Force $apkSource $apkTarget
$sha256 = (Get-FileHash -Algorithm SHA256 $apkTarget).Hash.ToLowerInvariant()

$manifest = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkFile = $apkName
    sha256 = $sha256
    forceUpdate = [bool]$ForceUpdate
    releaseNotes = @($ReleaseNotes)
}

$manifestJson = $manifest | ConvertTo-Json -Depth 4
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($manifestTarget, $manifestJson, $utf8WithoutBom)
Write-Host "APK ready: $apkTarget"
Write-Host "Manifest ready: $manifestTarget"
Write-Host "SHA-256: $sha256"
