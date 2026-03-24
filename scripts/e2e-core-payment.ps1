param(
    [string]$CoreBaseUrl = "http://localhost:8081",
    [string]$PaymentBaseUrl = "http://localhost:8083",
    [string]$JwtSecret = "change-this-dev-secret-to-a-long-random-value-32b",
    [string]$PaymentCallbackSecret = "dev-payment-callback-secret",
    [int]$PaymentWaitSeconds = 90,
    [int]$BookingSyncWaitSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    $raw = [Convert]::ToBase64String($Bytes)
    return $raw.TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-Hs256Jwt {
    param(
        [string]$Subject,
        [string[]]$Roles,
        [string]$Secret,
        [int]$ValidSeconds = 3600
    )

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $payloadJson = @{
        sub = $Subject
        roles = $Roles
        iat = $now
        exp = $now + $ValidSeconds
    } | ConvertTo-Json -Compress

    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsigned = "$header.$payload"

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signatureBytes = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned))
    } finally {
        $hmac.Dispose()
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

Write-Host "Checking health endpoints..."
$null = Invoke-JsonApi -Method GET -Url "$CoreBaseUrl/actuator/health" -Headers @{}
$null = Invoke-JsonApi -Method GET -Url "$PaymentBaseUrl/actuator/health" -Headers @{}

$ownerId = [Guid]::NewGuid().ToString()
$token = New-Hs256Jwt -Subject $ownerId -Roles @("OWNER") -Secret $JwtSecret
$authHeaders = @{ Authorization = "Bearer $token" }

Write-Host "Creating venue..."
$venueResp = Invoke-JsonApi -Method POST -Url "$CoreBaseUrl/api/core/venues" -Headers $authHeaders -Body @{
    name = "E2E Venue $(Get-Date -Format 'yyyyMMdd-HHmmss')"
    address = "E2E Address"
}
if (-not $venueResp.success) {
    throw "Create venue failed."
}
$venueId = [string]$venueResp.data.id

Write-Host "Creating court..."
$courtResp = Invoke-JsonApi -Method POST -Url "$CoreBaseUrl/api/core/courts" -Headers $authHeaders -Body @{
    venueId = $venueId
    name = "E2E Court"
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

Write-Host "Creating booking draft..."
$draftResp = Invoke-JsonApi -Method POST -Url "$CoreBaseUrl/api/core/bookings/draft" -Headers $authHeaders -Body @{
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

Write-Host "Waiting payment transaction from booking.events..."
$paymentTx = Wait-ForCondition -TimeoutSeconds $PaymentWaitSeconds -IntervalSeconds 2 -Description "payment transaction creation" -Probe {
    try {
        $payments = Invoke-JsonApi -Method GET -Url "$PaymentBaseUrl/api/payments/booking/$bookingId" -Headers $authHeaders
        if ($payments -is [System.Array] -and $payments.Count -gt 0) {
            return $payments[0]
        }
        return $null
    } catch {
        return $null
    }
}
$paymentId = [string]$paymentTx.id

Write-Host "Sending payment callback SUCCESS..."
$callbackHeaders = @{ "X-Payment-Callback-Secret" = $PaymentCallbackSecret }
$callbackResp = Invoke-JsonApi -Method POST -Url "$PaymentBaseUrl/api/payments/callback" -Headers $callbackHeaders -Body @{
    paymentId = $paymentId
    providerReference = "e2e-$(New-Guid)"
    success = $true
    failureReason = $null
}
if ([string]$callbackResp.status -ne "SUCCESS") {
    throw "Payment callback did not return SUCCESS."
}

Write-Host "Waiting core-service sync from payment.events..."
$syncedBooking = Wait-ForCondition -TimeoutSeconds $BookingSyncWaitSeconds -IntervalSeconds 2 -Description "booking sync from payment event" -Probe {
    try {
        $bookingResp = Invoke-JsonApi -Method GET -Url "$CoreBaseUrl/api/core/bookings/$bookingId" -Headers $authHeaders
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
$availabilityResp = Invoke-JsonApi -Method GET -Url "$CoreBaseUrl/api/core/availability?courtId=$courtId&start=$encodedStart&end=$encodedEnd" -Headers @{}
if (-not $availabilityResp.success) {
    throw "Availability API returned failure."
}
if ($availabilityResp.data.available -ne $false) {
    throw "Expected availability=false for booked time slot."
}

Write-Host ""
Write-Host "E2E cross-service SUCCESS."
Write-Host "bookingId: $bookingId"
Write-Host "paymentId: $paymentId"
Write-Host "status: $($syncedBooking.status), paymentStatus: $($syncedBooking.paymentStatus)"
