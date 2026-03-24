param(
    [int]$CoreDbPort = 3306,
    [int]$PaymentDbPort = 3307,
    [int]$AuthDbPort = 3308,
    [switch]$StartServiceContainers
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$composeFile = "infra/docker/docker-compose.yml"

function Wait-ContainerState {
    param(
        [string]$ContainerName,
        [ValidateSet("running", "healthy")] [string]$TargetState,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $state = docker inspect --format "{{.State.Status}}" $ContainerName 2>$null
        if ($LASTEXITCODE -eq 0) {
            if ($TargetState -eq "running" -and $state -eq "running") {
                return
            }
            if ($TargetState -eq "healthy") {
                $health = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" $ContainerName 2>$null
                if ($health -eq "healthy") {
                    return
                }
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timeout waiting container '$ContainerName' to be $TargetState."
}

function Test-ActuatorUp {
    param([string]$BaseUrl)
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 3
        return $health.status -eq "UP"
    } catch {
        return $false
    }
}

Write-Host "Checking Docker daemon..."
docker info --format "{{.ServerVersion}}|{{.OSType}}" | Out-Null

$env:MYSQL_CORE_PORT = $CoreDbPort
$env:MYSQL_PAYMENT_PORT = $PaymentDbPort
$env:MYSQL_AUTH_PORT = $AuthDbPort

Write-Host "Starting infra containers (mysql-core, mysql-payment, mysql-auth, kafka)..."
try {
    docker compose -f $composeFile up -d mysql-core mysql-payment mysql-auth kafka
} catch {
    Write-Host "Docker compose failed. If port is already used, retry with custom ports." -ForegroundColor Yellow
    Write-Host "Example: powershell -ExecutionPolicy Bypass -File scripts/dev-step1-up.ps1 -CoreDbPort 13306 -PaymentDbPort 13307 -AuthDbPort 13308"
    throw
}

Wait-ContainerState -ContainerName "sc-mysql-core" -TargetState healthy
Wait-ContainerState -ContainerName "sc-mysql-payment" -TargetState healthy
Wait-ContainerState -ContainerName "sc-mysql-auth" -TargetState healthy
Wait-ContainerState -ContainerName "sc-kafka" -TargetState running

Write-Host "Infra is ready."
Write-Host "DB ports: core=$CoreDbPort payment=$PaymentDbPort auth=$AuthDbPort"

if ($StartServiceContainers) {
    Write-Host "Starting service containers (core-service, payment-service, auth-service, notification-service, reporting-service, chatbot-service)..."
    docker compose -f $composeFile up -d core-service payment-service auth-service notification-service reporting-service chatbot-service
    Start-Sleep -Seconds 3
}

$coreUp = Test-ActuatorUp -BaseUrl "http://localhost:8081"
$paymentUp = Test-ActuatorUp -BaseUrl "http://localhost:8083"
$authUp = Test-ActuatorUp -BaseUrl "http://localhost:8082"
$notificationUp = Test-ActuatorUp -BaseUrl "http://localhost:8084"
$reportingUp = Test-ActuatorUp -BaseUrl "http://localhost:8085"
$chatbotUp = Test-ActuatorUp -BaseUrl "http://localhost:8086"

Write-Host ""
Write-Host "Health summary:"
Write-Host " - core-service    : $coreUp (http://localhost:8081/actuator/health)"
Write-Host " - payment-service : $paymentUp (http://localhost:8083/actuator/health)"
Write-Host " - auth-service    : $authUp (http://localhost:8082/actuator/health)"
Write-Host " - notification-service : $notificationUp (http://localhost:8084/actuator/health)"
Write-Host " - reporting-service    : $reportingUp (http://localhost:8085/actuator/health)"
Write-Host " - chatbot-service      : $chatbotUp (http://localhost:8086/actuator/health)"

if (-not $StartServiceContainers) {
    Write-Host ""
    Write-Host "If you run services from IDE/terminal, set DB ports first:"
    Write-Host " - CORE_DB_PORT=$CoreDbPort"
    Write-Host " - PAYMENT_DB_PORT=$PaymentDbPort"
    Write-Host " - AUTH_DB_PORT=$AuthDbPort"
}

Write-Host ""
Write-Host "Step 1 environment check completed."
