# ==============================================================================
# NexusFlow - Graceful Shutdown Script
# ==============================================================================
$Port = 8085

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " ENCERRANDO SERVICOS DA PLATAFORMA NEXUSFLOW" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. FINALIZAR PROCESSO DA APLICACAO NA PORTA 8085
Write-Host "[1/2] Encerrando aplicacao Spring Boot na porta $Port..." -ForegroundColor Yellow

$killed = $false
try {
    $occupied = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
    if ($occupied) {
        foreach ($conn in $occupied) {
            $pidToKill = $conn.OwningProcess
            if ($pidToKill -gt 0) {
                Write-Host "  [!] Finalizando processo PID $pidToKill..." -ForegroundColor Magenta
                Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                $killed = $true
            }
        }
    }
} catch {}

if (-not $killed) {
    $netstatOutput = netstat -ano | findstr ":$Port"
    if ($netstatOutput) {
        $lines = $netstatOutput -split "`n"
        foreach ($line in $lines) {
            if ($line -match "LISTENING\s+(\d+)") {
                $pidToKill = $matches[1]
                Write-Host "  [!] Finalizando processo PID $pidToKill..." -ForegroundColor Magenta
                Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                $killed = $true
            }
        }
    }
}

if ($killed) {
    Write-Host "  [OK] Aplicacao Spring Boot finalizada com sucesso." -ForegroundColor Green
} else {
    Write-Host "  [i] Nenhuma aplicacao estava rodando na porta $Port." -ForegroundColor DarkGray
}

# 2. PARAR CONTAINERS DO DOCKER COMPOSE
Write-Host ""
Write-Host "[2/2] Parando containers Docker (Postgres, Redis, Kafka, Prometheus, Grafana)..." -ForegroundColor Yellow

& docker compose -f docker/docker-compose.yml stop

Write-Host "  [OK] Containers pausados." -ForegroundColor Green
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " [OK] Todos os servicos foram encerrados com seguranca." -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
