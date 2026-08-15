param(
    [string]$VersionName = "1.6.0",
    [int]$VersionCode = 8,
    [string[]]$ReleaseNotes = @(
        "首页新增牌局恢复入口，并支持意外断线自动重连",
        "新增服务端授权的超级管理入口、账号查询和密码重置",
        "管理页可查看账号、在线玩家和房间运行概览",
        "登录失效与房间恢复失败改为结构化错误处理",
        "补充 HTTPS/WSS 长期部署模板与操作说明"
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
