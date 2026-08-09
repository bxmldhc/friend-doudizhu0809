$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$out = Join-Path $root 'svg\cards'
$suits = @(@('heart','&#9829;','#e45c4c'),@('diamond','&#9830;','#e45c4c'),@('club','&#9827;','#173c47'),@('spade','&#9824;','#173c47'))
$ranks = @('3','4','5','6','7','8','9','10','j','q','k','a','2')
foreach($s in $suits){ foreach($r in $ranks){
  $label = $r.ToUpper(); $svg = @"
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 224"><rect x="4" y="4" width="152" height="216" rx="14" fill="#fffdf7" stroke="#d9ded8" stroke-width="4"/><text x="18" y="43" fill="$($s[2])" font-family="sans-serif" font-size="27" font-weight="700">$label</text><text x="80" y="142" text-anchor="middle" fill="$($s[2])" font-size="88">$($s[1])</text></svg>
"@
  Set-Content -LiteralPath (Join-Path $out "card-$($s[0])-$r.svg") -Value $svg -Encoding utf8
}}
$jokers=@(@('small','&#23567;&#29579;','#173c47'),@('big','&#22823;&#29579;','#ef704e'))
foreach($j in $jokers){ $svg=@"
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 224"><rect x="4" y="4" width="152" height="216" rx="14" fill="#fffdf7" stroke="$($j[2])" stroke-width="5"/><circle cx="80" cy="105" r="50" fill="#164a52"/><path d="m80 55 10 28 30 2-23 19 8 30-25-16-25 16 8-30-23-19 30-2z" fill="#f2bb56"/><text x="80" y="202" text-anchor="middle" fill="$($j[2])" font-family="sans-serif" font-size="17" font-weight="700">$($j[1])</text></svg>
"@
Set-Content -LiteralPath (Join-Path $out "card-joker-$($j[0]).svg") -Value $svg -Encoding utf8 }
