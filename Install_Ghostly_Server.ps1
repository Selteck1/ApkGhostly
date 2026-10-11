
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Write-Host 'Ghostly Server installer - Minecraft 1.20.1 Forge'
Write-Host 'This installs Ghostly Server and the official Playit Companion dependency.'
Write-Host ''

$defaultMods = Join-Path $env:APPDATA '.minecraft\mods'
$answer = Read-Host "Mods folder path (press Enter for $defaultMods)"
if ([string]::IsNullOrWhiteSpace($answer)) { $modsDir = $defaultMods } else { $modsDir = $answer.Trim().Trim('"') }

if (!(Test-Path -LiteralPath $modsDir)) {
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null
}
$modsDir = (Resolve-Path -LiteralPath $modsDir).Path
Write-Host "Destination: $modsDir"
Write-Host ''

$ghostlyUrl = 'https://github.com/Selteck1/ApkGhostly/releases/latest/download/ghostly-server-1.0.0.jar'
$ghostlyPath = Join-Path $modsDir 'ghostly-server-1.0.0.jar'
$ghostlyTemp = Join-Path $env:TEMP ('ghostly-server-' + [guid]::NewGuid().ToString('N') + '.jar')

try {
    Write-Host 'Downloading Ghostly Server...'
    Invoke-WebRequest -Uri $ghostlyUrl -OutFile $ghostlyTemp -UseBasicParsing
    if (!(Test-Path $ghostlyTemp) -or (Get-Item $ghostlyTemp).Length -lt 5000) {
        throw 'The Ghostly Server release file is missing or too small. Check the GitHub release page.'
    }
    Move-Item -LiteralPath $ghostlyTemp -Destination $ghostlyPath -Force
} catch {
    if (Test-Path $ghostlyTemp) { Remove-Item $ghostlyTemp -Force -ErrorAction SilentlyContinue }
    Write-Host 'Could not download Ghostly Server.' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host 'You can check the build/release here: https://github.com/Selteck1/ApkGhostly/actions'
    exit 1
}

$companionPath = Join-Path $modsDir 'playit_companion-forge-0.1.1.jar'
$companionApi = 'https://api.modrinth.com/v2/version/TOoEgxGf'
$companionTemp = Join-Path $env:TEMP ('playit-companion-' + [guid]::NewGuid().ToString('N') + '.jar')
try {
    Write-Host 'Resolving the official Forge Playit Companion file...'
    $release = Invoke-RestMethod -Uri $companionApi -Method Get
    $candidate = $release.files | Where-Object { $_.filename -eq 'playit_companion-forge-0.1.1.jar' } | Select-Object -First 1
    if ($null -eq $candidate) { throw 'The expected Forge JAR is not listed by Modrinth.' }
    Write-Host 'Downloading Playit Companion...'
    Invoke-WebRequest -Uri $candidate.url -OutFile $companionTemp -UseBasicParsing
    if (!(Test-Path $companionTemp) -or (Get-Item $companionTemp).Length -lt 50000) {
        throw 'The Playit Companion JAR is missing or too small.'
    }
    Move-Item -LiteralPath $companionTemp -Destination $companionPath -Force
} catch {
    if (Test-Path $companionTemp) { Remove-Item $companionTemp -Force -ErrorAction SilentlyContinue }
    Write-Host 'Ghostly Server was installed, but Playit Companion could not be downloaded.' -ForegroundColor Yellow
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    Write-Host 'Download the Forge build for Minecraft 1.20.1 from: https://modrinth.com/plugin/playit-companion'
    exit 1
}

Write-Host ''
Write-Host 'Installation complete.' -ForegroundColor Green
Write-Host 'Restart the Minecraft Launcher and use your Forge 1.20.1 profile.'
Write-Host 'Load a singleplayer world, press Esc > Server > Open Access.'
Write-Host 'On first use, follow the Playit claim URL in Minecraft chat. After claiming, share the address shown in chat.'
Write-Host 'Do not share the one-time claim link; share only the server address.'
