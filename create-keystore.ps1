# PowerShell script to create Release Keystore for SMS Checker

$ErrorActionPreference = "Stop"

Write-Host "Creating Release Keystore for SMS Checker..." -ForegroundColor Cyan
Write-Host ""

# Find keytool
$keytoolPaths = @(
    "C:\Program Files\Java\*\bin\keytool.exe",
    "C:\Program Files\Eclipse Adoptium\*\bin\keytool.exe",
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
    "C:\Program Files\Android\Android Studio\jre\bin\keytool.exe"
)

if ($env:JAVA_HOME) {
    $keytoolPaths += "$env:JAVA_HOME\bin\keytool.exe"
}

$keytool = $null
foreach ($path in $keytoolPaths) {
    $found = Get-ChildItem -Path $path -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        $keytool = $found.FullName
        break
    }
}

if (-not $keytool) {
    Write-Host "ERROR: keytool not found" -ForegroundColor Red
    Write-Host ""
    Write-Host "Solutions:" -ForegroundColor Yellow
    Write-Host "  1. Use Android Studio: Build > Generate Signed Bundle / APK" -ForegroundColor White
    Write-Host "  2. Or install JDK from: https://adoptium.net" -ForegroundColor White
    Write-Host ""
    Write-Host "See guide: CREATE_KEYSTORE_GUIDE.md" -ForegroundColor Cyan
    exit 1
}

Write-Host "Found keytool: $keytool" -ForegroundColor Green
Write-Host ""

# Configuration
$keystoreFile = "smschecker-release.keystore"
$keystoreAlias = "smschecker-release"
$keystorePassword = "android123"
$keyPassword = "android123"
$dname = "CN=SMS Checker, OU=ThaiPrompt, O=ThaiPrompt, L=Bangkok, ST=Bangkok, C=TH"

# Check if keystore exists
if (Test-Path $keystoreFile) {
    Write-Host "WARNING: Keystore already exists: $keystoreFile" -ForegroundColor Yellow
    $response = Read-Host "Overwrite? (y/N)"
    if ($response -ne "y" -and $response -ne "Y") {
        Write-Host "Cancelled" -ForegroundColor Red
        exit 1
    }
    Remove-Item $keystoreFile
}

# Create keystore
Write-Host "Creating keystore..." -ForegroundColor Cyan

& $keytool -genkeypair -v `
    -keystore $keystoreFile `
    -alias $keystoreAlias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $keystorePassword `
    -keypass $keyPassword `
    -dname $dname

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Failed to create keystore" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "SUCCESS: Keystore created!" -ForegroundColor Green
Write-Host ""
Write-Host "Keystore Information:" -ForegroundColor Cyan
Write-Host "  File: $keystoreFile" -ForegroundColor White
Write-Host "  Alias: $keystoreAlias" -ForegroundColor White
Write-Host "  Password: $keystorePassword" -ForegroundColor White
Write-Host "  Key Password: $keyPassword" -ForegroundColor White
Write-Host ""
Write-Host "IMPORTANT:" -ForegroundColor Yellow
Write-Host "  - Keep this file safe" -ForegroundColor White
Write-Host "  - Never share with anyone" -ForegroundColor White
Write-Host "  - Never commit to Git" -ForegroundColor White
Write-Host "  - Backup to USB, Cloud" -ForegroundColor White
Write-Host ""

# Create keystore.properties
Write-Host "Creating keystore.properties..." -ForegroundColor Cyan

$propertiesContent = @"
storeFile=$keystoreFile
storePassword=$keystorePassword
keyAlias=$keystoreAlias
keyPassword=$keyPassword
"@

Set-Content -Path "keystore.properties" -Value $propertiesContent -Encoding UTF8

Write-Host "SUCCESS: Created keystore.properties" -ForegroundColor Green
Write-Host ""

# Convert keystore to Base64
Write-Host "Converting keystore to Base64..." -ForegroundColor Cyan

$keystoreBytes = [System.IO.File]::ReadAllBytes($keystoreFile)
$base64 = [Convert]::ToBase64String($keystoreBytes)
Set-Content -Path "keystore-base64.txt" -Value $base64 -Encoding UTF8

Write-Host "SUCCESS: Converted to Base64: keystore-base64.txt" -ForegroundColor Green
Write-Host ""

# Summary
Write-Host "DONE! Next steps:" -ForegroundColor Green
Write-Host ""
Write-Host "1. Add GitHub Secrets:" -ForegroundColor Cyan
Write-Host "   https://github.com/xjanova/smschecker/settings/secrets/actions" -ForegroundColor White
Write-Host ""
Write-Host "   Add these 4 secrets:" -ForegroundColor Yellow
Write-Host "   - SIGNING_KEYSTORE_BASE64 = content from keystore-base64.txt" -ForegroundColor White
Write-Host "   - SIGNING_STORE_PASSWORD = android123" -ForegroundColor White
Write-Host "   - SIGNING_KEY_ALIAS = smschecker-release" -ForegroundColor White
Write-Host "   - SIGNING_KEY_PASSWORD = android123" -ForegroundColor White
Write-Host ""
Write-Host "2. Test Build:" -ForegroundColor Cyan
Write-Host "   ./gradlew assembleRelease" -ForegroundColor White
Write-Host ""
Write-Host "Documentation:" -ForegroundColor Cyan
Write-Host "   - CREATE_KEYSTORE_GUIDE.md" -ForegroundColor White
Write-Host "   - SIGNING_SETUP.md" -ForegroundColor White
Write-Host ""
