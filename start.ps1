# ==============================================================================
# NexusFlow - All-in-One Startup Automation Script
# ==============================================================================
$ErrorActionPreference = "Continue"

$Port = 8085

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " NEXUSFLOW PLATFORM - INICIALIZACAO AUTOMATICA COMPLETA" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# 0. ROTINA DE LIMPEZA: LIBERAR A PORTA 8085 SE HOUVER PROCESSO ANTERIOR PRESO
Write-Host "[0/4] Verificando se a porta $Port esta livre..." -ForegroundColor Yellow

try {
    $occupied = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
    if ($occupied) {
        foreach ($conn in $occupied) {
            $pidToKill = $conn.OwningProcess
            if ($pidToKill -gt 0) {
                Write-Host "  [!] Encontrado processo anterior (PID: $pidToKill) na porta $Port. Finalizando..." -ForegroundColor Magenta
                Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                Start-Sleep -Seconds 1
            }
        }
        Write-Host "  [OK] Porta $Port liberada com sucesso!" -ForegroundColor Green
    } else {
        Write-Host "  [OK] Porta $Port livre para uso." -ForegroundColor Green
    }
} catch {
    # Fallback usando netstat
    $netstatOutput = netstat -ano | findstr ":$Port"
    if ($netstatOutput) {
        $lines = $netstatOutput -split "`n"
        foreach ($line in $lines) {
            if ($line -match "LISTENING\s+(\d+)") {
                $pidToKill = $matches[1]
                Write-Host "  [!] Finalizando processo PID $pidToKill na porta $Port..." -ForegroundColor Magenta
                Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

# 1. VERIFICAR / INICIAR DOCKER DESKTOP
Write-Host ""
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
Write-Host "   - Painel Web:   http://localhost:$Port" -ForegroundColor DarkCyan
Write-Host "   - Swagger UI:   http://localhost:$Port/swagger-ui.html" -ForegroundColor DarkCyan
Write-Host "   - Actuator:     http://localhost:$Port/actuator/health" -ForegroundColor DarkCyan
Write-Host "   - Prometheus:   http://localhost:$Port/actuator/prometheus" -ForegroundColor DarkCyan
Write-Host "   - Grafana:      http://localhost:3000 (admin/admin)" -ForegroundColor DarkCyan
Write-Host "   - pgAdmin:      http://localhost:5050 (admin@nexusflow.com/admin)" -ForegroundColor DarkCyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

& .\mvnw.cmd spring-boot:run
