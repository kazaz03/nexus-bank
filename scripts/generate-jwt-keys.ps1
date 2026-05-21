# ─────────────────────────────────────────────────────────────────────────────
# generate-jwt-keys.ps1
#
# Generates an RSA 2048-bit keypair used by JWT signing (RS256) and places
# the keys where the services expect them:
#   user-service/src/main/resources/keys/private.pem   (signing)
#   user-service/src/main/resources/keys/public.pem    (verification)
#   api-gateway/src/main/resources/keys/public.pem     (verification at the gateway)
#
# Run once per fresh clone. The keys are git-ignored on purpose — never commit
# them. Each developer should generate their own keypair locally.
#
# Requires OpenSSL on PATH (Git Bash ships with it).
# ─────────────────────────────────────────────────────────────────────────────

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$userKeysDir    = Join-Path $root "user-service\src\main\resources\keys"
$gatewayKeysDir = Join-Path $root "api-gateway\src\main\resources\keys"

New-Item -Path $userKeysDir    -ItemType Directory -Force | Out-Null
New-Item -Path $gatewayKeysDir -ItemType Directory -Force | Out-Null

$privatePem = Join-Path $userKeysDir "private.pem"
$publicPem  = Join-Path $userKeysDir "public.pem"

Write-Host "Generating RSA private key..."
& openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privatePem

Write-Host "Extracting public key..."
& openssl rsa -pubout -in $privatePem -out $publicPem 2>$null

Write-Host "Copying public key to api-gateway..."
Copy-Item $publicPem (Join-Path $gatewayKeysDir "public.pem") -Force

Write-Host ""
Write-Host "Done. Keys generated and placed:"
Write-Host "  $privatePem"
Write-Host "  $publicPem"
Write-Host "  $(Join-Path $gatewayKeysDir 'public.pem')"
