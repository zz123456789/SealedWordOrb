param(
    [string[]]$Versions = @('0.1.0', '1.0.0'),
    [switch]$SubmitForReview
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$credentialsPath = Join-Path $env:LOCALAPPDATA 'SealedWordOrbPublishing\credentials.json'
$credentials = Get-Content -LiteralPath $credentialsPath -Raw | ConvertFrom-Json
$token = [Net.NetworkCredential]::new('', (ConvertTo-SecureString $credentials.modrinth)).Password
$headers = @{ Authorization = $token; 'User-Agent' = 'SealedWordOrb-Publisher/1.0' }
$api = 'https://api.modrinth.com/v2'
$metadata = Get-Content (Join-Path $PSScriptRoot 'project.json') -Raw | ConvertFrom-Json -AsHashtable
$metadata.body = Get-Content (Join-Path $PSScriptRoot 'DESCRIPTION.en.md') -Raw
$statePath = Join-Path $PSScriptRoot 'modrinth-state.json'
$account = Invoke-RestMethod "$api/user" -Headers $headers

if (Test-Path -LiteralPath $statePath) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $project = Invoke-RestMethod "$api/project/$($state.project_id)" -Headers $headers
} else {
    $projects = Invoke-RestMethod "$api/user/$($account.id)/projects" -Headers $headers
    $project = $projects | Where-Object { $_.slug -eq $metadata.slug } | Select-Object -First 1
    if (-not $project) {
        $form = [ordered]@{
            data = ($metadata | ConvertTo-Json -Depth 12 -Compress)
            icon = Get-Item -LiteralPath (Join-Path $PSScriptRoot 'icon.png')
        }
        $project = Invoke-RestMethod "$api/project" -Method Post -Headers $headers -Form $form
    }
    @{ project_id = $project.id; slug = $project.slug; account = $account.username } |
        ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
}

$update = @{
    title = $metadata.title; description = $metadata.description; body = $metadata.body
    categories = $metadata.categories; client_side = 'required'; server_side = 'required'
}
Invoke-RestMethod "$api/project/$($project.id)" -Method Patch -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body ($update | ConvertTo-Json -Depth 8 -Compress) | Out-Null

foreach ($version in $Versions) {
    $jar = Get-Item -LiteralPath (Join-Path $root "build\libs\sealedwordorb-$version.jar")
    $hash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA512).Hash.ToLowerInvariant()
    $existingVersions = Invoke-RestMethod "$api/project/$($project.id)/version" -Headers $headers
    $existing = $existingVersions | Where-Object { $_.version_number -eq $version } | Select-Object -First 1
    if ($existing) {
        if (-not ($existing.files | Where-Object { $_.hashes.sha512 -eq $hash })) {
            throw "Version $version already exists with different content; refusing to overwrite it."
        }
        Write-Output "Verified existing Modrinth version $version ($($existing.id))."
        continue
    }
    $versionData = @{
        project_id = $project.id; name = "Sealed Word Orb $version (Forge 1.20.1)"
        version_number = $version
        changelog = Get-Content -LiteralPath (Join-Path $PSScriptRoot "CHANGELOG-$version.en.md") -Raw
        dependencies = @(); game_versions = @('1.20.1'); loaders = @('forge')
        version_type = $(if ($version -eq '0.1.0') { 'beta' } else { 'release' })
        featured = ($version -ne '0.1.0'); file_parts = @('file'); primary_file = 'file'
        environment = 'client_and_server'
    }
    $form = [ordered]@{ data = ($versionData | ConvertTo-Json -Depth 12 -Compress); file = $jar }
    $uploaded = Invoke-RestMethod "$api/version" -Method Post -Headers $headers -Form $form
    if (-not ($uploaded.files | Where-Object { $_.hashes.sha512 -eq $hash })) {
        throw "Server hash verification failed for $version."
    }
    Write-Output "Uploaded and verified Modrinth version $version ($($uploaded.id))."
}

$project = Invoke-RestMethod "$api/project/$($project.id)" -Headers $headers
if ($SubmitForReview -and $project.status -eq 'draft') {
    Invoke-RestMethod "$api/project/$($project.id)" -Method Patch -Headers $headers `
        -ContentType 'application/json; charset=utf-8' `
        -Body '{"status":"processing","requested_status":"approved"}' | Out-Null
}
$project = Invoke-RestMethod "$api/project/$($project.id)" -Headers $headers
@{ project_id = $project.id; slug = $project.slug; account = $account.username; status = $project.status
    url = "https://modrinth.com/mod/$($project.slug)"; verified_at = (Get-Date).ToUniversalTime().ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
Write-Output "Project: https://modrinth.com/mod/$($project.slug) | Status: $($project.status)"
$headers.Clear()
$token = $null
