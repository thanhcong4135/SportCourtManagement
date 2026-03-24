param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AuthBaseUrl = "http://localhost:8082",
    [string]$CoreBaseUrl = "http://localhost:8081",
    [string]$JwtSecret = "change-this-dev-secret-to-a-long-random-value-32b",
    [switch]$SkipOwnerScenario
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    $raw = [Convert]::ToBase64String($Bytes)
    return $raw.TrimEnd("=").Replace("+", "-").Replace("/", "_")
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

function Get-HttpStatusCode {
    param($Exception)

    if ($null -ne $Exception -and $null -ne $Exception.Response) {
        $statusCode = $Exception.Response.StatusCode
        if ($null -ne $statusCode) {
            if ($statusCode -is [int]) {
                return [int]$statusCode
            }
            if ($null -ne $statusCode.value__) {
                return [int]$statusCode.value__
            }
            return [int]$statusCode
        }
    }

    if ($null -ne $Exception -and $null -ne $Exception.Message) {
        $match = [Regex]::Match($Exception.Message, "\((\d{3})\)")
        if ($match.Success) {
            return [int]$match.Groups[1].Value
        }
    }

    return -1
}

function Invoke-JsonApi {
    param(
        [ValidateSet("GET", "POST", "PUT")] [string]$Method,
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

function Assert-FailsWithStatus {
    param(
        [scriptblock]$Action,
        [int]$ExpectedStatus,
        [string]$Description
    )

    try {
        & $Action | Out-Null
        throw "Expected HTTP $ExpectedStatus but call succeeded: $Description"
    } catch {
        $actual = Get-HttpStatusCode $_.Exception
        if ($actual -ne $ExpectedStatus) {
            throw "Expected HTTP $ExpectedStatus but got ${actual}: $Description"
        }
        Write-Host "OK: $Description -> HTTP $actual"
    }
}

Write-Host "Checking health endpoints..."
$null = Invoke-JsonApi -Method GET -Url "$GatewayBaseUrl/actuator/health" -Headers @{}
$null = Invoke-JsonApi -Method GET -Url "$AuthBaseUrl/actuator/health" -Headers @{}
$null = Invoke-JsonApi -Method GET -Url "$CoreBaseUrl/actuator/health" -Headers @{}

$stamp = Get-Date -Format "yyyyMMddHHmmssfff"
$email = "gateway.e2e.$stamp@test.local"
$password = "StrongPass123!"
$displayName = "Gateway E2E $stamp"

Write-Host "Registering customer via gateway..."
$registerResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/auth/register" -Headers @{} -Body @{
    email = $email
    password = $password
    displayName = $displayName
}
if ($null -eq $registerResp.userId -or [string]::IsNullOrWhiteSpace([string]$registerResp.accessToken)) {
    throw "Register did not return userId/accessToken."
}

Write-Host "Logging in via gateway..."
$loginResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/auth/login" -Headers @{} -Body @{
    email = $email
    password = $password
}
if ([string]$loginResp.userId -ne [string]$registerResp.userId) {
    throw "Login userId does not match register userId."
}
$customerToken = [string]$loginResp.accessToken
$customerRefreshToken = [string]$loginResp.refreshToken
$customerHeaders = @{ Authorization = "Bearer $customerToken" }

Write-Host "Checking /api/auth/me via gateway..."
$meResp = Invoke-JsonApi -Method GET -Url "$GatewayBaseUrl/api/auth/me" -Headers $customerHeaders
if ([string]$meResp.userId -ne [string]$loginResp.userId) {
    throw "Me endpoint returned unexpected userId."
}

Write-Host "Validating gateway security (anonymous -> bookings blocked)..."
Assert-FailsWithStatus -ExpectedStatus 401 -Description "Anonymous call to /api/core/bookings must be unauthorized" -Action {
    Invoke-JsonApi -Method GET -Url "$GatewayBaseUrl/api/core/bookings" -Headers @{}
}

Write-Host "Validating gateway security (customer -> bookings allowed)..."
$bookingListResp = Invoke-JsonApi -Method GET -Url "$GatewayBaseUrl/api/core/bookings?page=0&size=5" -Headers $customerHeaders
if (-not $bookingListResp.success) {
    throw "Customer call to /api/core/bookings failed."
}

Write-Host "Validating gateway security (customer -> venue create forbidden)..."
Assert-FailsWithStatus -ExpectedStatus 403 -Description "Customer call to POST /api/core/venues must be forbidden" -Action {
    Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/core/venues" -Headers $customerHeaders -Body @{
        name = "Forbidden Venue"
        address = "N/A"
    }
}

if (-not $SkipOwnerScenario) {
    Write-Host "Promoting user role via auth admin endpoint..."
    $adminToken = New-Hs256Jwt -Subject ([Guid]::NewGuid().ToString()) -Roles @("ADMIN") -Secret $JwtSecret
    $adminHeaders = @{ Authorization = "Bearer $adminToken" }

    $promoteResp = Invoke-JsonApi -Method PUT -Url "$GatewayBaseUrl/api/auth/admin/users/$($loginResp.userId)/roles" -Headers $adminHeaders -Body @{
        roles = @("ROLE_CUSTOMER", "ROLE_OWNER")
    }
    if (-not ($promoteResp.roles -contains "ROLE_OWNER")) {
        throw "Role update did not contain ROLE_OWNER."
    }

    Write-Host "Re-login and verify OWNER can create venue/court..."
    $ownerLoginResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/auth/login" -Headers @{} -Body @{
        email = $email
        password = $password
    }
    if (-not ($ownerLoginResp.roles -contains "OWNER")) {
        throw "Owner login token does not include OWNER role."
    }
    $ownerHeaders = @{ Authorization = "Bearer $($ownerLoginResp.accessToken)" }

    $venueResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/core/venues" -Headers $ownerHeaders -Body @{
        name = "GW Owner Venue $stamp"
        address = "E2E Address"
    }
    if (-not $venueResp.success) {
        throw "Owner failed to create venue."
    }
    $venueId = [string]$venueResp.data.id

    $courtResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/core/courts" -Headers $ownerHeaders -Body @{
        venueId = $venueId
        name = "GW Court $stamp"
        sportType = "BADMINTON"
    }
    if (-not $courtResp.success) {
        throw "Owner failed to create court."
    }
}

Write-Host "Testing refresh token via gateway..."
$refreshResp = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/auth/refresh" -Headers @{} -Body @{
    refreshToken = $customerRefreshToken
}
if ([string]::IsNullOrWhiteSpace([string]$refreshResp.refreshToken)) {
    throw "Refresh did not return refreshToken."
}
if ([string]$refreshResp.refreshToken -eq [string]$customerRefreshToken) {
    throw "Refresh token was not rotated."
}

Write-Host "Testing logout and revoked refresh token..."
$null = Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/auth/logout" -Headers @{} -Body @{
    refreshToken = [string]$refreshResp.refreshToken
}

Assert-FailsWithStatus -ExpectedStatus 401 -Description "Revoked refresh token must be rejected" -Action {
    Invoke-JsonApi -Method POST -Url "$GatewayBaseUrl/api/auth/refresh" -Headers @{} -Body @{
        refreshToken = [string]$refreshResp.refreshToken
    }
}

Write-Host ""
Write-Host "E2E gateway + auth SUCCESS."
Write-Host "email: $email"
Write-Host "userId: $($loginResp.userId)"
