$resource = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
if (-not (Test-Path -LiteralPath $edge)) { throw 'Microsoft Edge not found.' }
$targets = @(
  @{ Name = 'drawable-xhdpi'; Scale = 1 },
  @{ Name = 'drawable-xxhdpi'; Scale = 1.5 },
  @{ Name = 'drawable-xxxhdpi'; Scale = 2 }
)
$svgFiles = Get-ChildItem -LiteralPath (Join-Path $resource 'svg') -Filter '*.svg' -File -Recurse | Where-Object { $_.DirectoryName -notlike '*\tools' }
foreach ($target in $targets) {
  $targetDir = Join-Path $resource (Join-Path 'png\android' $target.Name)
  New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
  foreach ($file in $svgFiles) {
    $rawSvg = Get-Content -LiteralPath $file.FullName -Raw
    $viewBoxMatch = [regex]::Match($rawSvg, 'viewBox\s*=\s*"([^"]+)"')
    if (-not $viewBoxMatch.Success) {
      Write-Warning "Skipping SVG without viewBox: $($file.FullName)"
      continue
    }
    $viewBox = $viewBoxMatch.Groups[1].Value -split '\s+'
    $width = [int]([double]$viewBox[2] * $target.Scale)
    $height = [int]([double]$viewBox[3] * $target.Scale)
    $name = ($file.Directory.Name + '-' + $file.BaseName).ToLowerInvariant() + '.png'
    $output = Join-Path $targetDir $name
    $uri = [uri]::new($file.FullName).AbsoluteUri
    & $edge --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size="$width,$height" --screenshot="$output" $uri 2>$null | Out-Null
  }
}
