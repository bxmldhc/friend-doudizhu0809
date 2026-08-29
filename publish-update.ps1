param(
    [string]$VersionName = "2.0.1",
    [int]$VersionCode = 14,
    [string[]]$ReleaseNotes = @(
        "新增小游戏站首页",
        "保留斗地主创建房间、加入房间、继续房间和最近战绩入口",
        "优化首页横屏布局与快捷入口滚动"
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
$sha256Algorithm = [System.Security.Cryptography.SHA256]::Create()
try {
    $apkStream = [System.IO.File]::OpenRead($apkTarget)
    try {
        $sha256 = ([System.BitConverter]::ToString(
            $sha256Algorithm.ComputeHash($apkStream)
        ) -replace "-", "").ToLowerInvariant()
    } finally {
        $apkStream.Dispose()
    }
} finally {
    $sha256Algorithm.Dispose()
}

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
