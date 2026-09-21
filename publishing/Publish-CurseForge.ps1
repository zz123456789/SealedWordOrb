param(
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][int]$ProjectId,
    [string[]]$Versions = @('0.1.0', '1.0.0')
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$credentials = Get-Content (Join-Path $env:LOCALAPPDATA 'SealedWordOrbPublishing\credentials.json') -Raw | ConvertFrom-Json
$token = [Net.NetworkCredential]::new('', (ConvertTo-SecureString $credentials.curseforge)).Password
$headers = @{ 'X-Api-Token' = $token }
$api = 'https://minecraft.curseforge.com/api'
$statePath = Join-Path $PSScriptRoot 'curseforge-state.json'
$state = if (Test-Path $statePath) { Get-Content $statePath -Raw | ConvertFrom-Json -AsHashtable } else { @{ project_id = $ProjectId; files = @{} } }
if ($state.project_id -ne $ProjectId) { throw 'The saved project ID does not match; check the intended destination.' }
$gameVersions = Invoke-RestMethod "$api/game/versions" -Headers $headers
$versionTypes = Invoke-RestMethod "$api/game/version-types" -Headers $headers
$minecraftType = $versionTypes | Where-Object { $_.slug -eq 'minecraft-1-20' } | Select-Object -First 1
$minecraftVersion = $gameVersions | Where-Object { $_.name -eq '1.20.1' -and $_.gameVersionTypeID -eq $minecraftType.id } | Select-Object -First 1
$forge = $gameVersions | Where-Object { $_.name -eq 'Forge' } | Select-Object -First 1
$java = $gameVersions | Where-Object { $_.name -eq 'Java 17' } | Select-Object -First 1
$environmentType = $versionTypes | Where-Object { $_.slug -eq 'environment' } | Select-Object -First 1
$client = $gameVersions | Where-Object { $_.name -eq 'Client' -and $_.gameVersionTypeID -eq $environmentType.id } | Select-Object -First 1
$server = $gameVersions | Where-Object { $_.name -eq 'Server' -and $_.gameVersionTypeID -eq $environmentType.id } | Select-Object -First 1
if (-not $minecraftVersion -or -not $forge -or -not $java -or -not $client -or -not $server) { throw 'Required CurseForge game-version or environment tags were not found.' }

foreach ($version in $Versions) {
    $file = Get-Item (Join-Path $root "build\libs\sealedwordorb-$version.jar")
    $hash = (Get-FileHash $file.FullName -Algorithm SHA256).Hash
    if ($state.files.ContainsKey($version)) {
        if ($state.files[$version].sha256 -ne $hash) { throw "Version $version differs from its previously uploaded content." }
        Write-Output "Upload already recorded for ${version}: file $($state.files[$version].file_id)."
        continue
    }
    $metadata = @{
        changelog = Get-Content (Join-Path $PSScriptRoot "CHANGELOG-$version.en.md") -Raw
        changelogType = 'markdown'; displayName = "Sealed Word Orb $version (Forge 1.20.1)"
        gameVersions = @($minecraftVersion.id, $forge.id, $java.id, $client.id, $server.id)
        releaseType = $(if ($version -eq '0.1.0') { 'beta' } else { 'release' })
        isMarkedForManualRelease = $false
    }
    $form = [ordered]@{ metadata = ($metadata | ConvertTo-Json -Depth 8 -Compress); file = $file }
    # No automatic retry: an interrupted upload must be checked in the author dashboard first.
    $result = Invoke-RestMethod "$api/projects/$ProjectId/upload-file" -Method Post -Headers $headers -Form $form
    if (-not $result.id) { throw 'Upload returned no file ID. Check the author dashboard before retrying.' }
    $state.files[$version] = @{ file_id = $result.id; sha256 = $hash; uploaded_at = (Get-Date).ToUniversalTime().ToString('o') }
    $state | ConvertTo-Json -Depth 8 | Set-Content $statePath -Encoding utf8
    Write-Output "CurseForge accepted $version as file $($result.id); moderation may still be pending."
}
$headers.Clear()
$token = $null
