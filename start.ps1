# ==============================================================================
# NexusFlow — All-in-One Startup Automation Script
# ==============================================================================
# 1. Starts Docker Desktop (if not running) and waits for Docker daemon
# 2. Starts all infrastructure containers via Docker Compose (Postgres, Redis, Kafka, Prometheus, Grafana)
# 3. Waits for PostgreSQL to be fully ready on port 5433
# 4. Launches the NexusFlow Spring Boot Application
# ==============================================================================

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " 🚀 NEXUSFLOW PLATFORM — INICIALIZAÇÃO AUTOMÁTICA COMPLETA" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# ------------------------------------------------------------------------------
# 1. VERIFICAR / INICIAR DOCKER DESKTOP
# ------------------------------------------------------------------------------
Write-Host "🔍 [1/4] Verificando status do Docker Desktop..." -ForegroundColor Yellow

$dockerReady = $false
docker ps 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    $dockerReady = $true
    Write-Host "  ✅ Docker daemon já está em execução!" -ForegroundColor Green
} else {
    Write-Host "  ⚡ Docker Desktop está fechado. Iniciando aplicativo..." -ForegroundColor Magenta
    
    $dockerPath = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerPath) {
        Start-Process $dockerPath
    } else {
        Write-Host "  ⚠️ Executável do Docker Desktop não encontrado em $dockerPath." -ForegroundColor Red
        Write-Host "     Por favor, abra o Docker Desktop manualmente." -ForegroundColor Yellow
    }

    Write-Host "  ⏳ Aguardando o Docker daemon inicializar (isso pode levar até 30s)..." -ForegroundColor DarkGray
    for ($i = 1; $i -le 40; $i++) {
        Start-Sleep -Seconds 2
        docker ps 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $dockerReady = $true
            Write-Host "  ✅ Docker daemon conectado e pronto!" -ForegroundColor Green
            break
        }
        Write-Host -NoNewline "."
    }
    Write-Host ""
}

if (-not $dockerReady) {
    Write-Host "❌ Não foi possível conectar ao Docker. Verifique se o Docker Desktop abriu corretamente e tente novamente." -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 2. SUBIR CONTAINERS DO DOCKER COMPOSE
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "📦 [2/4] Subindo infraestrutura (Postgres 16, Redis 7, Kafka, Prometheus, Grafana)..." -ForegroundColor Yellow

docker compose -f docker/docker-compose.yml up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Falha ao subir os containers via docker compose." -ForegroundColor Red
    exit 1
}
Write-Host "  ✅ Containers de infraestrutura iniciados em background!" -ForegroundColor Green

# ------------------------------------------------------------------------------
# 3. AGUARDAR O POSTGRESQL ESTAR PRONTO NA PORTA 5433
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "⏳ [3/4] Aguardando PostgreSQL (porta 5433) aceitar conexões..." -ForegroundColor Yellow

$dbReady = $false
for ($i = 1; $i -le 25; $i++) {
    docker exec nexusflow-postgres pg_isready -U nexusflow_user -d nexusflow_db 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $dbReady = $true
        Write-Host "  ✅ PostgreSQL está pronto e aceitando conexões!" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 1
    Write-Host -NoNewline "."
}
Write-Host ""

if (-not $dbReady) {
    Write-Host "⚠️ PostgreSQL demorou para responder, tentando prosseguir..." -ForegroundColor Yellow
}

# ------------------------------------------------------------------------------
# 4. INICIAR A APLICAÇÃO SPRING BOOT
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "☕ [4/4] Inicializando aplicação Spring Boot (NexusFlow)..." -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " 🌐 Endpoints que estarão disponíveis:" -ForegroundColor Cyan
Write-Host "   • Swagger UI:   http://localhost:8080/swagger-ui.html" -ForegroundColor DarkCyan
Write-Host "   • Actuator:     http://localhost:8080/actuator/health" -ForegroundColor DarkCyan
Write-Host "   • Prometheus:   http://localhost:8080/actuator/prometheus" -ForegroundColor DarkCyan
Write-Host "   • Grafana:      http://localhost:3000 (admin/admin)" -ForegroundColor DarkCyan
Write-Host "   • pgAdmin:      http://localhost:5050 (admin@nexusflow.com/admin)" -ForegroundColor DarkCyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

.\mvnw.cmd spring-boot:run
