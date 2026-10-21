<#
.SYNOPSIS
    Authenticode-sign the Windows binary of a published release and re-pack its MCP Bundle.

.DESCRIPTION
    The release workflow signs and notarizes the macOS binary in CI, but publishes the Windows binary unsigned:
    Windows code signing uses a Certum certificate through SimplySign Desktop, which needs an interactive login
    (OTP from the mobile app) and has no clean headless path on GitHub-hosted runners. This script closes the gap
    after the fact, the same way thegreystone/mcp-email and thegreystone/diskspace do it:

      1. downloads openjdk-helper-<version>-windows-x86_64.exe from the GitHub release,
      2. signs it (SHA-256, RFC 3161 timestamp from Certum) and verifies signature + timestamp,
      3. re-creates openjdk-helper-<version>-windows-x86_64.mcpb around the signed binary with mcpb/pack.mjs
         and runs mcpb/test-mcpb.mjs against it, so the signed executable is exercised before upload,
      4. uploads both assets back to the release, replacing the unsigned ones.

    Preconditions: SimplySign Desktop logged in (cert visible in Cert:\CurrentUser\My); signtool (Windows SDK);
    gh authenticated against the repo; Node.js 22+ with "npm ci --prefix mcpb" already run.

.PARAMETER Version
    Release version without the "v" prefix, e.g. 0.2.0.

.PARAMETER Thumbprint
    SHA-1 thumbprint of the code-signing certificate. Defaults to $env:CERTUM_SIGN_THUMBPRINT, then
    $env:DISKSPACE_SIGN_THUMBPRINT (same certificate). Set one of them in your $PROFILE: the thumbprint is not
    secret, but keeping it out of the repo makes certificate renewal a one-line edit instead of a commit.
    Always pin by thumbprint; never use "signtool /a", which can silently pick the wrong certificate.

.PARAMETER NoUpload
    Sign, pack and test under target\signed but do not touch the GitHub release.

.EXAMPLE
    .\mcpb\Sign-Release.ps1 -Version 0.2.0
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $Version,
    [string] $Thumbprint,
    [string] $Repo = 'thegreystone/openjdk-helper',
    [string] $TimestampUrl = 'http://time.certum.pl',
    [string] $SignTool,
    [switch] $NoUpload
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

if (-not $Thumbprint) { $Thumbprint = $env:CERTUM_SIGN_THUMBPRINT }
if (-not $Thumbprint) { $Thumbprint = $env:DISKSPACE_SIGN_THUMBPRINT }
if (-not $Thumbprint) {
    throw 'No thumbprint. Set $env:CERTUM_SIGN_THUMBPRINT in your $PROFILE or pass -Thumbprint.'
}

if (-not $SignTool) {
    $SignTool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin" -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\x64\\' } |
        Sort-Object FullName -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $SignTool) {
        throw 'signtool.exe not found under Windows Kits 10. Install the Windows SDK or pass -SignTool.'
    }
}

if (-not (Test-Path 'mcpb\node_modules\@anthropic-ai\mcpb')) {
    throw 'Bundle tools are missing. Run "npm ci --prefix mcpb" first.'
}

$cert = Get-ChildItem Cert:\CurrentUser\My | Where-Object { $_.Thumbprint -eq $Thumbprint }
if (-not $cert) {
    throw "Cert $Thumbprint not found in Cert:\CurrentUser\My. Open SimplySign Desktop and log in (OTP from the mobile app), then retry."
}
Write-Host "Signing as: $($cert.Subject) (expires $($cert.NotAfter.ToShortDateString()))"

$binary = "openjdk-helper-$Version-windows-x86_64.exe"
$bundle = "openjdk-helper-$Version-windows-x86_64.mcpb"
$work = Join-Path (Get-Location) 'target\signed'
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force $work | Out-Null

function Invoke-SignAndVerify([string] $path, [string] $label) {
    Write-Host "Signing $label..."
    & $SignTool sign /tr $TimestampUrl /td sha256 /fd sha256 /sha1 $Thumbprint /d 'OpenJDK Helper' /du "https://github.com/$Repo" $path
    if ($LASTEXITCODE -ne 0) { throw "signtool failed for $label (exit $LASTEXITCODE)" }
    $sig = Get-AuthenticodeSignature $path
    if ($sig.Status -ne 'Valid') { throw "Signature on $label is $($sig.Status): $($sig.StatusMessage)" }
    if (-not $sig.TimeStamperCertificate) {
        throw "Signature on $label has no timestamp; without it the signature expires with the certificate."
    }
    Write-Host "  Subject:   $($sig.SignerCertificate.Subject)"
    Write-Host "  Timestamp: $($sig.TimeStamperCertificate.Subject)"
}

# 1. Download the unsigned binary.
Write-Host "Downloading $binary from release v$Version..."
$release = gh release view "v$Version" --repo $Repo --json tagName 2>$null
if ($LASTEXITCODE -ne 0 -or -not $release) { throw "release v$Version not found on $Repo" }
gh release download "v$Version" --repo $Repo --pattern $binary --dir $work
if ($LASTEXITCODE -ne 0) { throw "gh release download failed for $binary (exit $LASTEXITCODE)" }
$exe = Join-Path $work $binary

# 2. Sign and verify.
Invoke-SignAndVerify $exe $binary

# 3. Re-pack the bundle around the signed binary and smoke-test it. The manifest carries no file hashes, so a
#    re-pack around a signed binary is a valid bundle; pack.mjs checks the PE architecture and the manifest.
Write-Host "Re-packing $bundle..."
$packed = Join-Path $work $bundle
node mcpb/pack.mjs $Version windows-x86_64 $exe $packed
if ($LASTEXITCODE -ne 0) { throw "mcpb/pack.mjs failed (exit $LASTEXITCODE)" }
Write-Host "Testing $bundle (MCP initialize, tools/list and a census lookup over stdio)..."
node mcpb/test-mcpb.mjs $packed
if ($LASTEXITCODE -ne 0) { throw "mcpb/test-mcpb.mjs failed (exit $LASTEXITCODE)" }

if ($NoUpload) {
    Write-Host "Signed artifacts left in $work (upload skipped)."
    return
}

# 4. Replace the release assets.
Write-Host "Replacing release assets..."
gh release upload "v$Version" --repo $Repo --clobber $exe $packed
if ($LASTEXITCODE -ne 0) { throw "gh release upload failed (exit $LASTEXITCODE)" }
Write-Host "Done. Release v$Version now has a signed $binary and a $bundle wrapping it." -ForegroundColor Green
