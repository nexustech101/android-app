# Builds a signed release APK into dist/ and, with -Publish, creates the GitHub release the app updates from.
#
#   .\release.ps1                      # build dist\SiteSnap-<version>.apk only
#   .\release.ps1 -Publish             # build, then create release v<version> with the APK attached
#   .\release.ps1 -Publish -Notes "Fixes camera flash on Pixel 6"
#
# Before each release, bump versionCode and versionName in app/build.gradle.kts.
param(
    [switch]$Publish,
    [string]$Notes = ""
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$gradleFile = Get-Content app/build.gradle.kts -Raw
$version = [regex]::Match($gradleFile, 'versionName = "([^"]+)"').Groups[1].Value
$repoLine = Select-String -Path gradle.properties -Pattern '^sitesnap\.githubRepo=(.*)$'
$repo = if ($repoLine) { $repoLine.Matches[0].Groups[1].Value.Trim() } else { "" }

if (-not (Test-Path signing/keystore.properties)) {
    throw "signing/keystore.properties is missing. Every release must be signed with the same key or phones will refuse the update."
}
if (-not $repo) {
    Write-Warning "sitesnap.githubRepo is empty in gradle.properties, so this build will not check GitHub for updates."
}
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }

& .\gradlew.bat :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

New-Item -ItemType Directory -Force dist | Out-Null
$apk = "dist/SiteSnap-$version.apk"
Copy-Item app/build/outputs/apk/release/app-release.apk $apk -Force
Write-Host "Built $apk"

if ($Publish) {
    if (-not $repo) { throw "Set sitesnap.githubRepo in gradle.properties before publishing." }
    # Must be a normal (not draft, not pre-release) release: the app reads /releases/latest.
    $ghArgs = @("release", "create", "v$version", $apk, "--repo", $repo, "--title", "SiteSnap $version")
    # Notes go through a file: Windows PowerShell mangles native arguments that contain double quotes.
    $notesFile = $null
    if ($Notes) {
        $notesFile = New-TemporaryFile
        [IO.File]::WriteAllText($notesFile.FullName, $Notes, (New-Object Text.UTF8Encoding $false))
        $ghArgs += @("--notes-file", $notesFile.FullName)
    } else { $ghArgs += "--generate-notes" }
    try { & gh @ghArgs } finally { if ($notesFile) { Remove-Item $notesFile.FullName -Force } }
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }
    Write-Host "Published v$version to https://github.com/$repo/releases"
}
