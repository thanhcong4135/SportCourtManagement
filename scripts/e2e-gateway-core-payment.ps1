param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AuthBaseUrl = "http://localhost:8082",
    [string]$JwtIssuer = "https://auth.sportcourt.local",
    [string]$JwtKid = "sc-auth-rs256-v1",
    [string]$JwtPrivateKeyPath = "services/auth-service/src/main/resources/keys/dev-rs256-private.pem",
    [string]$PaymentCallbackSecret = "dev-payment-callback-secret",
    [int]$PaymentWaitSeconds = 90,
    [int]$BookingSyncWaitSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    $raw = [Convert]::ToBase64String($Bytes)
    return $raw.TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function New-Rs256Jwt {
    param(
        [string]$Subject,
        [string[]]$Roles,
        [string]$Issuer,
        [string]$Kid,
        [string]$PrivateKeyPath,
        [int]$ValidSeconds = 3600
    )

    if (-not (Test-Path -LiteralPath $PrivateKeyPath)) {
        throw "JWT private key not found at path: $PrivateKeyPath"
    }

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = @{
        alg = "RS256"
        typ = "JWT"
        kid = $Kid
    } | ConvertTo-Json -Compress
    $payloadJson = @{
        sub = $Subject
        roles = $Roles
        iss = $Issuer
        iat = $now
        exp = $now + $ValidSeconds
    } | ConvertTo-Json -Compress

    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsigned = "$header.$payload"

    $pem = Get-Content -LiteralPath $PrivateKeyPath -Raw
    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        if ($rsa.PSObject.Methods.Name -contains "ImportFromPem") {
            $rsa.ImportFromPem($pem)
        } else {
            if ($env:OS -ne "Windows_NT") {
                throw "Current PowerShell runtime does not support ImportFromPem and Windows CNG fallback is unavailable."
            }
            $base64 = ($pem -replace '-----BEGIN PRIVATE KEY-----', '' -replace '-----END PRIVATE KEY-----', '' -replace '\s', '')
            $pkcs8Bytes = [Convert]::FromBase64String($base64)
            $key = [System.Security.Cryptography.CngKey]::Import($pkcs8Bytes, [System.Security.Cryptography.CngKeyBlobFormat]::Pkcs8PrivateBlob)
            $rsa.Dispose()
            $rsa = [System.Security.Cryptography.RSACng]::new($key)
        }

        $signatureBytes = $rsa.SignData(
            [Text.Encoding]::UTF8.GetBytes($unsigned),
            [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
        )
    } finally {
        $rsa.Dispose()
    }
    $signature = ConvertTo-Base64Url $signatureBytes
    return "$unsigned.$signature"
}

function Invoke-JsonApi {
    param(
        [ValidateSet("GET", "POST")] [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [object]$Body = $null
    )

    try {
        if ($null -eq $Body) {
            return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -TimeoutSec 20
        }

        $json = $Body | ConvertTo-Json -Depth 8
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body $json -TimeoutSec 20
    } catch {
        if ($null -ne $_.Exception.Response) {
            $stream = $_.Exception.Response.GetResponseStream()
            if ($null -ne $stream) {
                $reader = [System.IO.StreamReader]::new($stream)
                try {
                    $errorBody = $reader.ReadToEnd()
                    if (-not [string]::IsNullOrWhiteSpace($errorBody)) {
                        Write-Host "API error response: $errorBody" -ForegroundColor Yellow
                    }
                } finally {
                    $reader.Dispose()
                }
            }
        }
        throw
    }
}

function Wait-ForCondition {
    param(
        [int]$TimeoutSeconds,
        [int]$IntervalSeconds,
        [scriptblock]$Probe,
        [string]$Description
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $result = & $Probe
        if ($null -ne $result) {
            return $result
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
    throw "Timed out waiting for $Description (timeout=${TimeoutSeconds}s)."
}

function Assert-HealthEndpoint {
    param(
        [string]$Name,
        [string]$BaseUrl
    )

    $healthUrl = "$BaseUrl/actuator/health"
    Write-Host " - $Name => $healthUrl"
    try {
        $health = Invoke-JsonApi -Method GET -Url $healthUrl -Headers @{}
        if ($null -eq $health -or [string]$health.status -ne "UP") {
            throw "$Name health status is not UP"
        }
    } catch {
        throw "Health check failed for $Name at $healthUrl. $($_.Exception.Message)"
    }
}

Write-Host "Checking gateway health endpoint..."
Assert-HealthEndpoint -Name "api-gateway" -BaseUrl $GatewayBaseUrl
Assert-HealthEndpoint -Name "auth-service" -BaseUrl $AuthBaseUrl

$ownerId = [Guid]::NewGuid().ToString()
$token = New-Rs256Jwt -Subject $ownerId -Roles @("OWNER") -Issuer $JwtIssuer -Kid $JwtKid -PrivateKeyPath $JwtPrivateKeyPath
$authHeaders = @{ Authorization = "Bearer $token" }

Write-Host "Creating venue via gateway..."
$venueResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/core/venues" -Headers $authHeaders -Body @{
    name = "GW E2E Venue $(Get-Date -Format 'yyyyMMdd-HHmmss')"
    address = "GW E2E Address"
}
if (-not $venueResp.success) {
    throw "Create venue failed."
}
$venueId = [string]$venueResp.data.id

Write-Host "Creating court via gateway..."
$courtResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/core/courts" -Headers $authHeaders -Body @{
    venueId = $venueId
    name = "GW E2E Court"
    sportType = "BADMINTON"
}
if (-not $courtResp.success) {
    throw "Create court failed."
}
$courtId = [string]$courtResp.data.id

$nowUtc = [DateTimeOffset]::UtcNow
$nextSlotEpoch = [long]([Math]::Ceiling($nowUtc.ToUnixTimeSeconds() / 1800.0) * 1800)
$nextSlot = [DateTimeOffset]::FromUnixTimeSeconds($nextSlotEpoch)
$start = $nextSlot.AddHours(2)
$end = $start.AddHours(2)
$startIso = $start.ToString("o")
$endIso = $end.ToString("o")

Write-Host "Creating booking draft via gateway..."
$draftResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/core/bookings/draft" -Headers $authHeaders -Body @{
    courtId = $courtId
    customerId = $ownerId
    startTime = $startIso
    endTime = $endIso
    priceTotal = 400000
}
if (-not $draftResp.success) {
    throw "Create booking draft failed."
}
$bookingId = [string]$draftResp.data.id

Write-Host "Waiting payment transaction via gateway..."
$paymentTx = Wait-ForCondition -TimeoutSeconds $PaymentWaitSeconds -IntervalSeconds 2 -Description "payment transaction creation" -Probe {
    try {
        $payments = Invoke-JsonApi -Method GET -Url "$GatewayBaseUrl/api/payments/booking/$bookingId" -Headers $authHeaders
        if ($payments -is [System.Array] -and $payments.Count -gt 0) {
            return $payments[0]
        }
        return $null
    } catch {
        return $null
    }
}
$paymentId = [string]$paymentTx.id

Write-Host "Sending payment callback SUCCESS via gateway..."
$callbackHeaders = @{ "X-Payment-Callback-Secret" = $PaymentCallbackSecret }
$callbackResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/payments/callback" -Headers $callbackHeaders -Body @{
    paymentId = $paymentId
    providerReference = "gw-e2e-$(New-Guid)"
    success = $true
    failureReason = $null
}
if ([string]$callbackResp.status -ne "SUCCESS") {
    throw "Payment callback did not return SUCCESS."
}

Write-Host "Waiting core-service sync via gateway..."
$syncedBooking = Wait-ForCondition -TimeoutSeconds $BookingSyncWaitSeconds -IntervalSeconds 2 -Description "booking sync from payment event" -Probe {
    try {
        $bookingResp = Invoke-JsonApi -Method GET -Url "$GatewayBaseUrl/api/core/bookings/$bookingId" -Headers $authHeaders
        if ($bookingResp.success -and $bookingResp.data.paymentStatus -eq "DEPOSITED" -and $bookingResp.data.status -eq "CONFIRMED") {
            return $bookingResp.data
        }
        return $null
    } catch {
        return $null
    }
}

$encodedStart = [uri]::EscapeDataString($startIso)
$encodedEnd = [uri]::EscapeDataString($endIso)
$availabilityResp = Invoke-JsonApi -Method GET -Url "$GatewayBaseUrl/api/core/availability?courtId=$courtId&start=$encodedStart&end=$encodedEnd" -Headers @{}
if (-not $availabilityResp.success) {
    throw "Availability API returned failure."
}
if ($availabilityResp.data.available -ne $false) {
    throw "Expected availability=false for booked time slot."
}

Write-Host ""
Write-Host "E2E gateway-core-payment SUCCESS."
Write-Host "bookingId: $bookingId"
Write-Host "paymentId: $paymentId"
Write-Host "status: $($syncedBooking.status), paymentStatus: $($syncedBooking.paymentStatus)"
