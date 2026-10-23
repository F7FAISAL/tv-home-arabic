# Windows version of theme.sh: push a theme folder (config.json and optionally wallpaper.jpg) to the
# launcher on the TV and restart Home. See themes/README.md for the keys.
#
# Usage: tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
#        tools\theme.ps1 -TvHost 192.168.1.50 themes\madinah tvs\livingroom\config.local.json   # plus one TV's overlay
#        tools\theme.ps1 -TvHost 192.168.1.50 -Reset
param(
    [Parameter(Position = 0)][string]$Theme,
    [Parameter(Position = 1)][string]$TvConfig,
    [switch]$Reset,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
# Resolve the theme folder against the caller's directory before moving into tools\.
if ($Theme) { $Theme = (Resolve-Path $Theme).Path }
if ($TvConfig) { $TvConfig = (Resolve-Path $TvConfig).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$revision = [guid]::NewGuid().ToString('N')
# Restart Home so it rereads the theme. Android drops a force-stopped app's accessibility services from the
# enabled list (asynchronously, so wait a second), which would switch off InputKeyService/HomeKeyService;
# put the list back if it had ours. Writing the same list back is not enough: the force-stop also turns
# accessibility_enabled off, and the service only binds again once the list changes. So restart first, then
# clear the list, switch accessibility off (some TVs keep the service "crashed" and unbound otherwise), write
# the list back and switch accessibility on again.
$restart = "a=`$(settings get secure enabled_accessibility_services); am force-stop $pkg; am start -n $pkg/.MainActivity >/dev/null; case `"`$a`" in *$pkg*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services `"`$a`"; settings put secure accessibility_enabled 1 ;; esac"

# Some firmware (TCL G07, Android 11) locks the shell out of Android/data. Files are staged in
# /data/local/tmp and copied in, or written through the launcher's ThemeProvider when that is refused.
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-theme'
# A file's staging path; wallpapers/a.jpg is staged as $tmp-wallpapers-a.jpg.
function Stage($name) { "$tmp-$($name -replace '/', '-')" }
function Put($name) { $t = Stage $name; "cp $t $dir/$name 2>/dev/null || content write --uri $uri/$name < $t; rm -f $t" }
function Drop($name) { "rm -f $dir/$name 2>/dev/null; content delete --uri $uri/$name 2>/dev/null" }

if ($Reset) {
    & $py -I tv.py "mkdir -p $dir 2>/dev/null; $(Drop 'config.json'); $(Drop 'wallpaper.jpg'); printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision'); $restart; echo reset"
    exit 0
}
if (-not $Theme) { throw 'usage: theme.ps1 <theme folder> | -Reset' }
$config = Join-Path $Theme 'config.json'
if (-not (Test-Path $config)) { throw "no config.json in $Theme" }
try { Get-Content $config -Raw | ConvertFrom-Json | Out-Null } catch { throw "$config is not valid JSON" }

& $py -I tv.py "mkdir -p $dir 2>/dev/null" | Out-Null
# A theme's config.local.json (gitignored: the Drive link, the Thmanyah font choice), then the TV's own overlay if
# one was given (gitignored tvs\<tv>\config.local.json), are merged over config.json for the TV only, as theme.sh does.
foreach ($local in @((Join-Path $Theme 'config.local.json'), $TvConfig)) {
    if (-not $local -or -not (Test-Path $local)) { continue }
    $merged = [System.IO.Path]::GetTempFileName()
    & $py -I -c 'import json,sys; c=json.load(open(sys.argv[1],encoding="utf-8")); c.update(json.load(open(sys.argv[2],encoding="utf-8"))); json.dump(c,open(sys.argv[3],"w",encoding="utf-8"),ensure_ascii=False)' $config $local $merged
    if ($LASTEXITCODE -ne 0) { throw "$local is not valid JSON" }
    $config = $merged
}
& $py -I tv.py --push $config "$tmp-config.json" | Out-Null
& $py -I tv.py (Put 'config.json')
$wall = Join-Path $Theme 'wallpaper.jpg'
if (Test-Path $wall) { & $py -I b64push.py $wall "$tmp-wallpaper.jpg"; & $py -I tv.py (Put 'wallpaper.jpg') } else { & $py -I tv.py (Drop 'wallpaper.jpg') | Out-Null }
& $py -I tv.py "mkdir -p $dir/wallpapers 2>/dev/null; rm -f $dir/wallpapers/*.jpg $dir/wallpapers/*.jpeg $dir/wallpapers/*.png $dir/wallpapers/*.webp $dir/wallpapers/*.JPG $dir/wallpapers/*.JPEG $dir/wallpapers/*.PNG $dir/wallpapers/*.WEBP 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null" | Out-Null
$wallpapers = Join-Path $Theme 'wallpapers'
# "bundle_gallery": true pushes the repository's wallpapers/ (the online gallery's photos) when the theme has none.
if (-not (Test-Path $wallpapers -PathType Container) -and (Get-Content (Join-Path $Theme 'config.json') -Raw | ConvertFrom-Json).bundle_gallery -eq $true) {
    $wallpapers = Join-Path $PSScriptRoot '..\wallpapers'
}
if (Test-Path $wallpapers -PathType Container) {
    Get-ChildItem $wallpapers -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' } | ForEach-Object {
        if ($_.Name -notmatch '^[A-Za-z0-9_.-]+$') { throw "unsupported wallpaper filename: $($_.Name)" }
        & $py -I b64push.py $_.FullName (Stage "wallpapers/$($_.Name)")
        & $py -I tv.py (Put "wallpapers/$($_.Name)") | Out-Null
    }
}
# The owner's Thmanyah Sans (tools\install-thmanyah.py) for a theme with "font": "thmanyah": pushed unmodified to
# fonts/ on the TV (ThemeFonts), never into the APK; removed otherwise. As theme.sh does.
$fonts = Join-Path $PSScriptRoot '..\launcher\fonts-local\thmanyah'
$fontNames = @('thmanyahsans-Regular.otf', 'thmanyahsans-Bold.otf')
if ((Get-Content $config -Raw -Encoding UTF8 | ConvertFrom-Json).font -eq 'thmanyah') {
    if (@($fontNames | Where-Object { -not (Test-Path (Join-Path $fonts $_)) }).Count -eq 0) {
        & $py -I tv.py "mkdir -p $dir/fonts 2>/dev/null" | Out-Null
        foreach ($f in $fontNames) {
            & $py -I b64push.py (Join-Path $fonts $f) (Stage "fonts/$f")
            & $py -I tv.py (Put "fonts/$f") | Out-Null
        }
    } else {
        Write-Warning '"font": "thmanyah" but launcher\fonts-local\thmanyah\ is incomplete (tools\install-thmanyah.py); the TV keeps any copy it has, else IBM Plex'
    }
} else {
    foreach ($f in $fontNames) { & $py -I tv.py (Drop "fonts/$f") | Out-Null }
}
& $py -I tv.py "printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision')" | Out-Null
& $py -I tv.py "$restart; echo applied"
Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV. Undo with: tools\theme.ps1 -TvHost $TvHost -Reset"
