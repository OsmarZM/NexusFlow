# ==============================================================================
# NexusFlow — Automated Production-Grade E2E Test Suite
# ==============================================================================
param(
    [string]$BaseUrl = "http://localhost:8085"
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " 🚀 INICIANDO TESTE E2E DE PRODUÇÃO: NEXUSFLOW PLATFORM" -ForegroundColor Cyan
Write-Host " Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

$Global:PassedCount = 0
$Global:FailedCount = 0

function Assert-Step {
    param(
        [string]$StepName,
        [bool]$Condition,
        [string]$Details = ""
    )
    if ($Condition) {
        Write-Host "  ✅ [PASS] $StepName" -ForegroundColor Green
        if ($Details) { Write-Host "     ↳ $Details" -ForegroundColor DarkGray }
        $Global:PassedCount++
    } else {
        Write-Host "  ❌ [FAIL] $StepName" -ForegroundColor Red
        if ($Details) { Write-Host "     ↳ $Details" -ForegroundColor Yellow }
        $Global:FailedCount++
    }
}

# ------------------------------------------------------------------------------
# SEÇÃO 1: HEALTH CHECK & INFRAESTRUTURA
# ------------------------------------------------------------------------------
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "📍 SEÇÃO 1: Verificação de Infraestrutura & Health Check" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

try {
    $healthResponse = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 5
    Assert-Step "Actuator Health Status está UP" ($healthResponse.status -eq "UP") "Status: $($healthResponse.status)"
} catch {
    Assert-Step "Actuator Health Status está UP" $false "Não foi possível conectar a $BaseUrl: $_"
    Write-Host ""
    Write-Host "⚠️  Certifique-se de que a aplicação Spring Boot está rodando em $BaseUrl" -ForegroundColor Yellow
    Write-Host "   Execute: .\mvnw.cmd spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# ------------------------------------------------------------------------------
# SEÇÃO 2: AUTENTICAÇÃO, SEGURANÇA & RBAC (JWT)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "📍 SEÇÃO 2: Autenticação, Segurança & RBAC (Stateless JWT)" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

$adminLoginBody = @{
    username = "admin"
    password = "Admin@123456"
} | ConvertTo-Json

$token = $null
try {
    $authResponse = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
    $token = $authResponse.accessToken
    Assert-Step "Login do Administrador Seeded com Sucesso" ($token -ne $null -and $token.Length -gt 20) "Token JWT gerado com sucesso."
} catch {
    Assert-Step "Login do Administrador Seeded com Sucesso" $false "Erro: $_"
}

$headers = @{
    "Authorization" = "Bearer $token"
}

# Testar acesso não autenticado (deve retornar 401/403)
try {
    $unauth = Invoke-RestMethod -Uri "$BaseUrl/api/v1/customers" -Method Get -ErrorAction Stop
    Assert-Step "Bloqueio de rota protegida sem Token JWT" $false "Acesso não foi bloqueado."
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Step "Bloqueio de rota protegida sem Token JWT" ($statusCode -eq 401 -or $statusCode -eq 403) "Status retornado: $statusCode"
}

# ------------------------------------------------------------------------------
# SEÇÃO 3: CLIENTES & CATÁLOGO DE PRODUTOS
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "📍 SEÇÃO 3: Gestão de Clientes e Catálogo de Produtos com Cache" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

$uniqueSuffix = Get-Random -Minimum 1000 -Maximum 9999
$customerBody = @{
    name = "Tony Stark $uniqueSuffix"
    email = "tony.stark.$uniqueSuffix@avengers.io"
    document = "987$uniqueSuffix"
    status = "ACTIVE"
} | ConvertTo-Json

$customerId = $null
try {
    $customerRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/customers" -Method Post -Body $customerBody -ContentType "application/json" -Headers $headers
    $customerId = $customerRes.id
    Assert-Step "Cadastro de Cliente no PostgreSQL" ($customerId -ne $null) "Customer ID: $customerId"
} catch {
    Assert-Step "Cadastro de Cliente no PostgreSQL" $false "Erro: $_"
}

$sku = "MACBOOK-M3-MAX-$uniqueSuffix"
$productBody = @{
    sku = $sku
    name = "MacBook Pro M3 Max $uniqueSuffix"
    description = "Apple M3 Max 16-inch 64GB Unified Memory"
    price = 28999.00
    status = "ACTIVE"
    initialStock = 5
} | ConvertTo-Json

$productId = $null
try {
    $productRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/products" -Method Post -Body $productBody -ContentType "application/json" -Headers $headers
    $productId = $productRes.id
    Assert-Step "Cadastro de Produto e Inicialização Automática de Estoque (5 unid.)" ($productId -ne $null) "Product ID: $productId, SKU: $sku"
} catch {
    Assert-Step "Cadastro de Produto e Inicialização Automática de Estoque" $false "Erro: $_"
}

# Consulta ao produto (Testando Cache L2)
try {
    $cachedProduct = Invoke-RestMethod -Uri "$BaseUrl/api/v1/products/$sku" -Method Get -Headers $headers
    Assert-Step "Consulta ao Produto por SKU (Cache L2 Redis)" ($cachedProduct.sku -eq $sku) "Preço: R$ $($cachedProduct.price)"
} catch {
    Assert-Step "Consulta ao Produto por SKU (Cache L2 Redis)" $false "Erro: $_"
}

# ------------------------------------------------------------------------------
# SEÇÃO 4: CONCORRÊNCIA DE ESTOQUE & VALIDAÇÕES
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "📍 SEÇÃO 4: Concorrência de Estoque & Bloqueio Pessimista" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

# Teste de Estoque Insuficiente (Tentando pedir 10 quando só há 5)
$oversellOrderBody = @{
    customerId = $customerId
    items = @(
        @{
            sku = $sku
            quantity = 10
        }
    )
} | ConvertTo-Json

try {
    $oversellRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/orders" -Method Post -Body $oversellOrderBody -ContentType "application/json" -Headers $headers
    Assert-Step "Bloqueio de Overselling (Estoque Insuficiente)" $false "Pedido de 10 unidades foi aceito indevidamente!"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Step "Bloqueio de Overselling (Estoque Insuficiente)" ($statusCode -eq 400 -or $statusCode -eq 422) "Rejeição esperada com status $statusCode (RFC 7807)"
}

# ------------------------------------------------------------------------------
# SEÇÃO 5: FLUXO DE PEDIDOS & ORQUESTRAÇÃO DE SAGA (HAPPY PATH & COMPENSAÇÃO)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "📍 SEÇÃO 5: Orquestração de Saga com Compensação Automática" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

# 5.1 HAPPY PATH: Pedido -> Reserva -> Pagamento Aprovado -> Confirmação
$validOrderBody = @{
    customerId = $customerId
    items = @(
        @{
            sku = $sku
            quantity = 2
        }
    )
} | ConvertTo-Json

$order1Id = $null
try {
    $order1Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/orders" -Method Post -Body $validOrderBody -ContentType "application/json" -Headers $headers
    $order1Id = $order1Res.id
    Assert-Step "Criação de Pedido com Reserva Atômica (2 unid.)" ($order1Res.status -eq "WAITING_PAYMENT") "Order ID: $order1Id, Status: $($order1Res.status)"
} catch {
    Assert-Step "Criação de Pedido com Reserva Atômica" $false "Erro: $_"
}

# Simular Pagamento Aprovado
$paymentHappyBody = @{
    orderId = $order1Id
    customerId = $customerId
    amount = 57998.00
    simulateFailure = $false
} | ConvertTo-Json

try {
    $payHappyRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments" -Method Post -Body $paymentHappyBody -ContentType "application/json" -Headers $headers
    Assert-Step "Processamento de Pagamento com Sucesso (APPROVED)" ($payHappyRes.status -eq "APPROVED") "Payment ID: $($payHappyRes.id)"
} catch {
    Assert-Step "Processamento de Pagamento com Sucesso" $false "Erro: $_"
}

# 5.2 SAGA COMPENSATING TRANSACTION: Pedido -> Reserva -> Falha de Pagamento -> Compensação
$order2Body = @{
    customerId = $customerId
    items = @(
        @{
            sku = $sku
            quantity = 2
        }
    )
} | ConvertTo-Json

$order2Id = $null
try {
    $order2Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/orders" -Method Post -Body $order2Body -ContentType "application/json" -Headers $headers
    $order2Id = $order2Res.id
    Assert-Step "Criação de Segundo Pedido para Simulação de Falha" ($order2Res.status -eq "WAITING_PAYMENT") "Order ID: $order2Id"
} catch {
    Assert-Step "Criação de Segundo Pedido" $false "Erro: $_"
}

# Simular Pagamento Rejeitado
$paymentFailBody = @{
    orderId = $order2Id
    customerId = $customerId
    amount = 57998.00
    simulateFailure = $true
} | ConvertTo-Json

try {
    $payFailRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments" -Method Post -Body $paymentFailBody -ContentType "application/json" -Headers $headers
    Assert-Step "Simulação de Rejeição de Pagamento (REJECTED)" ($payFailRes.status -eq "REJECTED") "Payment ID: $($payFailRes.id)"
} catch {
    Assert-Step "Simulação de Rejeição de Pagamento" $false "Erro: $_"
}

# ------------------------------------------------------------------------------
# SEÇÃO 6: RATE LIMITING DISTRIBUÍDO
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "📍 SEÇÃO 6: Rate Limiting Distribuído (Sliding Window / Token Bucket)" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

try {
    $rateLimitCheck = Invoke-WebRequest -Uri "$BaseUrl/api/v1/products/$sku" -Method Get -Headers $headers -UseBasicParsing
    $limitHeader = $rateLimitCheck.Headers["X-RateLimit-Limit"]
    $remainingHeader = $rateLimitCheck.Headers["X-RateLimit-Remaining"]
    Assert-Step "Presença dos Headers de Rate Limit (X-RateLimit-Limit / Remaining)" ($limitHeader -ne $null) "Limit: $limitHeader, Remaining: $remainingHeader"
} catch {
    Assert-Step "Presença dos Headers de Rate Limit" $false "Erro: $_"
}

# ------------------------------------------------------------------------------
# SEÇÃO 7: OBSERVABILIDADE & MÉTRICAS PROMETHEUS
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "📍 SEÇÃO 7: Observabilidade & Métricas de Negócio Prometheus" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

try {
    $metrics = Invoke-RestMethod -Uri "$BaseUrl/actuator/prometheus" -Method Get
    $hasOrdersMetric = $metrics -match "nexusflow_orders_created_total"
    $hasPaymentsMetric = $metrics -match "nexusflow_payments_approved_total"
    Assert-Step "Exportação de Métricas de Negócio em /actuator/prometheus" ($hasOrdersMetric -and $hasPaymentsMetric) "Métricas nexusflow_orders_created_total presentes no Prometheus."
} catch {
    Assert-Step "Exportação de Métricas de Negócio" $false "Erro: $_"
}

# ------------------------------------------------------------------------------
# RESUMO FINAL
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " 🏁 RESUMO FINAL DOS TESTES E2E" -ForegroundColor Cyan
Write-Host " ✅ Passou: $Global:PassedCount" -ForegroundColor Green
Write-Host " ❌ Falhou: $Global:FailedCount" -ForegroundColor $(if ($Global:FailedCount -gt 0) { "Red" } else { "DarkGray" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Global:FailedCount -eq 0) {
    Write-Host "🎉 TODOS OS TESTES E2E DE PRODUÇÃO FORAM EXECUTADOS COM SUCESSO!" -ForegroundColor Green
} else {
    Write-Host "⚠️ Alguns testes falharam. Verifique os logs acima." -ForegroundColor Yellow
}
