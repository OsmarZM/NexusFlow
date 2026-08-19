# ==============================================================================
# NexusFlow - All-in-One Startup Automation Script
# ==============================================================================
$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " NEXUSFLOW PLATFORM - INICIALIZACAO AUTOMATICA COMPLETA" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. VERIFICAR / INICIAR DOCKER DESKTOP
Write-Host "[1/4] Verificando status do Docker..." -ForegroundColor Yellow

$dockerReady = $false
try {
    & docker ps 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $dockerReady = $true
        Write-Host "  [OK] Docker daemon esta em execucao!" -ForegroundColor Green
    }
} catch {
    $dockerReady = $false
}

if (-not $dockerReady) {
    Write-Host "  [!] Docker Desktop esta fechado. Iniciando aplicativo..." -ForegroundColor Magenta
    $dockerPath = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerPath) {
        Start-Process $dockerPath
    } else {
        Write-Host "  [WARN] Executavel nao encontrado em $dockerPath" -ForegroundColor Red
    }

    Write-Host "  Aguardando Docker daemon inicializar..." -ForegroundColor DarkGray
    for ($i = 1; $i -le 40; $i++) {
        Start-Sleep -Seconds 2
        try {
            & docker ps 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $dockerReady = $true
                Write-Host "  [OK] Docker daemon conectado e pronto!" -ForegroundColor Green
                break
            }
        } catch {}
        Write-Host -NoNewline "."
    }
    Write-Host ""
}

if (-not $dockerReady) {
    Write-Host "[ERRO] Nao foi possivel conectar ao Docker. Abra o Docker Desktop manualmente." -ForegroundColor Red
    exit 1
}

# 2. SUBIR CONTAINERS DO DOCKER COMPOSE
Write-Host ""
Write-Host "[2/4] Subindo infraestrutura (Postgres 16, Redis 7, Kafka, Prometheus, Grafana)..." -ForegroundColor Yellow

& docker compose -f docker/docker-compose.yml up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Falha ao subir os containers via docker compose." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Containers iniciados com sucesso!" -ForegroundColor Green

# 3. AGUARDAR O POSTGRESQL ESTAR PRONTO NA PORTA 5433
Write-Host ""
Write-Host "[3/4] Aguardando PostgreSQL (porta 5433) aceitar conexoes..." -ForegroundColor Yellow

$dbReady = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        & docker exec nexusflow-postgres pg_isready -U nexusflow_user -d nexusflow_db 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $dbReady = $true
            Write-Host "  [OK] PostgreSQL pronto e aceitando conexoes!" -ForegroundColor Green
            break
        }
    } catch {}
    Start-Sleep -Seconds 1
    Write-Host -NoNewline "."
}
Write-Host ""

if (-not $dbReady) {
    Write-Host "  [AVISO] Continuando inicializacao..." -ForegroundColor Yellow
}

# 4. INICIAR A APLICACAO SPRING BOOT
Write-Host ""
Write-Host "[4/4] Inicializando aplicacao Spring Boot (NexusFlow)..." -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Endpoints disponiveis apos inicializacao:" -ForegroundColor Cyan
Write-Host "   - Swagger UI:   http://localhost:8085/swagger-ui.html" -ForegroundColor DarkCyan
Write-Host "   - Actuator:     http://localhost:8085/actuator/health" -ForegroundColor DarkCyan
Write-Host "   - Prometheus:   http://localhost:8085/actuator/prometheus" -ForegroundColor DarkCyan
Write-Host "   - Grafana:      http://localhost:3000 (admin/admin)" -ForegroundColor DarkCyan
Write-Host "   - pgAdmin:      http://localhost:5050 (admin@nexusflow.com/admin)" -ForegroundColor DarkCyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

& .\mvnw.cmd spring-boot:run
