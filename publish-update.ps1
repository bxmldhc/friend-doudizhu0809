param(
    [string]$VersionName = "2.0.0",
    [int]$VersionCode = 13,
    [string[]]$ReleaseNotes = @(
        "三人都不叫地主时自动重新洗牌发牌",
        "新增地主与农民分阵营胜率",
        "大幅优化困难 AI 的叫抢、农民配合和残局策略",
        "优化 AI 对顺子、连对和飞机等牌型结构的保护"
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
