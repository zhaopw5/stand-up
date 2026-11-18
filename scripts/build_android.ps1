param(
    [string]$Keystore = $env:STANDUP_ANDROID_KEYSTORE,
    [string]$PasswordFile = $env:STANDUP_ANDROID_PASSWORD_FILE,
    [string]$UnsignedApk
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) {
    throw 'Set JAVA_HOME (JDK 17) and ANDROID_HOME (Android SDK) first.'
}
if (-not $Keystore -or -not (Test-Path -LiteralPath $Keystore)) {
    throw 'Provide the private release keystore with -Keystore. Never commit it.'
}
if (-not $PasswordFile -or -not (Test-Path -LiteralPath $PasswordFile)) {
    throw 'Provide the private password file with -PasswordFile. Never commit it.'
}
if (-not $UnsignedApk) {
    Push-Location (Join-Path $projectRoot 'android')
    try {
        & .\gradlew.bat --no-daemon testDebugUnitTest lintRelease assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Android tests, lint or build failed.' }
    } finally { Pop-Location }
    $UnsignedApk = Join-Path $projectRoot 'android\app\build\outputs\apk\release\app-release-unsigned.apk'
}
if (-not (Test-Path -LiteralPath $UnsignedApk)) { throw "APK not found: $UnsignedApk" }
$outputDir = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$aligned = Join-Path $outputDir 'stand-up-android-aligned.apk'
$outputApk = Join-Path $outputDir 'stand-up-android.apk'
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0'
& "$buildTools\zipalign.exe" -P 16 -f 4 $UnsignedApk $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
& "$buildTools\apksigner.bat" sign --ks $Keystore --ks-key-alias stand-up --ks-pass "file:$PasswordFile" --out $outputApk $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
& "$buildTools\apksigner.bat" verify --verbose --print-certs $outputApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$digest = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText((Join-Path $outputDir 'stand-up-android.sha256'), "$digest  stand-up-android.apk`n")
Write-Output "Verified signed APK: $outputApk"
