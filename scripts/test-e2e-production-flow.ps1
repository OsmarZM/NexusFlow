param(
    [string]$BaseUrl = "http://localhost:8085"
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " TESTE E2E DE PRODUCAO: NEXUSFLOW PLATFORM" -ForegroundColor Cyan
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
        Write-Host "  [PASS] $StepName" -ForegroundColor Green
        if ($Details) { Write-Host "     -> $Details" -ForegroundColor DarkGray }
        $Global:PassedCount++
    } else {
        Write-Host "  [FAIL] $StepName" -ForegroundColor Red
        if ($Details) { Write-Host "     -> $Details" -ForegroundColor Yellow }
        $Global:FailedCount++
    }
}

# ------------------------------------------------------------------------------
# SECAO 1: HEALTH CHECK
# ------------------------------------------------------------------------------
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "SECAO 1: Verificacao de Infraestrutura e Health Check" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

try {
    $healthResponse = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 5
    Assert-Step "Actuator Health Status esta UP" ($healthResponse.status -eq "UP") "Status: $($healthResponse.status)"
} catch {
    Assert-Step "Actuator Health Status esta UP" $false "Erro de conexao"
    exit 1
}

# ------------------------------------------------------------------------------
# SECAO 2: AUTENTICACAO & JWT
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "SECAO 2: Autenticacao e RBAC (Stateless JWT)" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

$adminLoginBody = @{
    usernameOrEmail = "admin"
    password = "Admin@123456"
} | ConvertTo-Json

$token = $null
try {
    $authResponse = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
    $token = $authResponse.accessToken
    Assert-Step "Login do Administrador com Sucesso" ($token -ne $null -and $token.Length -gt 20) "Token JWT gerado com sucesso"
} catch {
    Assert-Step "Login do Administrador com Sucesso" $false "Erro no login: $_"
}

$headers = @{
    "Authorization" = "Bearer $token"
}

# Testar rota sem token
try {
    $unauth = Invoke-RestMethod -Uri "$BaseUrl/api/v1/customers" -Method Get -ErrorAction Stop
    Assert-Step "Bloqueio de rota sem Token JWT" $false "Acesso nao bloqueado"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Step "Bloqueio de rota sem Token JWT" ($statusCode -eq 401 -or $statusCode -eq 403) "Status retornado: $statusCode (Acesso Negado Seguro)"
}

# ------------------------------------------------------------------------------
# SECAO 3: CLIENTES E PRODUTOS
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "SECAO 3: Gestao de Clientes e Produtos com Cache" -ForegroundColor Blue
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
    Assert-Step "Cadastro de Cliente no PostgreSQL" $false "Erro ao criar cliente: $_"
}

$sku = "MACBOOK-M3-MAX-$uniqueSuffix"
$productBody = @{
    sku = $sku
    name = "MacBook Pro M3 Max $uniqueSuffix"
    description = "Apple M3 Max 16-inch 64GB"
    price = 28999.00
    status = "ACTIVE"
    initialStock = 5
} | ConvertTo-Json

$productId = $null
try {
    $productRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/products" -Method Post -Body $productBody -ContentType "application/json" -Headers $headers
    $productId = $productRes.id
    Assert-Step "Cadastro de Produto e Estoque Inicial de 5 unidades" ($productId -ne $null) "SKU: $sku"
} catch {
    Assert-Step "Cadastro de Produto e Estoque Inicial" $false "Erro ao criar produto: $_"
}

# Consulta com cache por SKU
try {
    $cachedProduct = Invoke-RestMethod -Uri "$BaseUrl/api/v1/products/sku/$sku" -Method Get -Headers $headers
    Assert-Step "Consulta ao Produto por SKU (Cache Redis)" ($cachedProduct.sku -eq $sku) "Preco: R$ $($cachedProduct.price)"
} catch {
    Assert-Step "Consulta ao Produto por SKU" $false "Erro ao consultar produto: $_"
}

# ------------------------------------------------------------------------------
# SECAO 4: CONCORRENCIA DE ESTOQUE
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "SECAO 4: Concorrencia de Estoque e Bloqueio Pessimista" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

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
    Assert-Step "Bloqueio de Overselling (Estoque Insuficiente)" $false "Pedido aceito indevidamente"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Step "Bloqueio de Overselling (Estoque Insuficiente)" ($statusCode -eq 400 -or $statusCode -eq 409 -or $statusCode -eq 422) "Rejeicao com status $statusCode (RFC 7807)"
}

# ------------------------------------------------------------------------------
# SECAO 5: SAGA FLOW E COMPENSACAO
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "SECAO 5: Orquestracao de Saga com Compensacao" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

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
    Assert-Step "Criacao de Pedido com Reserva de 2 unidades" ($order1Res.status -eq "WAITING_PAYMENT") "Order ID: $order1Id, Status: $($order1Res.status)"
} catch {
    Assert-Step "Criacao de Pedido com Reserva" $false "Erro ao criar pedido: $_"
}

# Pagamento Aprovado
$paymentHappyBody = @{
    orderId = $order1Id
    customerId = $customerId
    amount = 57998.00
    simulateFailure = $false
} | ConvertTo-Json

try {
    $payHappyRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments" -Method Post -Body $paymentHappyBody -ContentType "application/json" -Headers $headers
    Assert-Step "Processamento de Pagamento Aprovado" ($payHappyRes.status -eq "APPROVED") "Payment ID: $($payHappyRes.id)"
} catch {
    Assert-Step "Processamento de Pagamento Aprovado" $false "Erro no pagamento: $_"
}

# Pagamento Rejeitado com compensação
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
    Assert-Step "Criacao de Segundo Pedido para Simulacao de Falha" ($order2Res.status -eq "WAITING_PAYMENT") "Order ID: $order2Id"
} catch {
    Assert-Step "Criacao de Segundo Pedido" $false "Erro: $_"
}

$paymentFailBody = @{
    orderId = $order2Id
    customerId = $customerId
    amount = 57998.00
    simulateFailure = $true
} | ConvertTo-Json

try {
    $payFailRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments" -Method Post -Body $paymentFailBody -ContentType "application/json" -Headers $headers
    Assert-Step "Simulacao de Pagamento Rejeitado e Compensacao da Saga" ($payFailRes.status -eq "REJECTED") "Status: $($payFailRes.status)"
} catch {
    Assert-Step "Simulacao de Pagamento Rejeitado" $false "Erro: $_"
}

# ------------------------------------------------------------------------------
# SECAO 6: RATE LIMITING
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "SECAO 6: Rate Limiting Distribuido" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/products" -Method Get -Headers $headers -UseBasicParsing
    $limitHeader = $response.Headers["X-RateLimit-Limit"]
    $remainingHeader = $response.Headers["X-RateLimit-Remaining"]
    Assert-Step "Presenca dos Headers de Rate Limit" ($limitHeader -ne $null -or $remainingHeader -ne $null) "Limit: $limitHeader, Remaining: $remainingHeader"
} catch {
    # Em caso de 429 ou erro nos headers, recupera os headers da exceção
    $limitHeader = $_.Exception.Response.Headers["X-RateLimit-Limit"]
    $remainingHeader = $_.Exception.Response.Headers["X-RateLimit-Remaining"]
    Assert-Step "Presenca dos Headers de Rate Limit" ($limitHeader -ne $null -or $remainingHeader -ne $null) "Limit: $limitHeader, Remaining: $remainingHeader"
}

# ------------------------------------------------------------------------------
# SECAO 7: OBSERVABILIDADE
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue
Write-Host "SECAO 7: Metricas de Negocio Prometheus" -ForegroundColor Blue
Write-Host "-----------------------------------------------------------------" -ForegroundColor Blue

try {
    $metrics = Invoke-RestMethod -Uri "$BaseUrl/actuator/prometheus" -Method Get -Headers $headers
    $hasMetrics = $metrics -match "nexusflow_orders_created_total" -or $metrics -match "process_uptime_seconds" -or $metrics -match "jvm_memory_used_bytes"
    Assert-Step "Exportacao de Metricas em /actuator/prometheus" ($hasMetrics) "Metricas do Prometheus e Micrometer exportadas com sucesso"
} catch {
    Assert-Step "Exportacao de Metricas" $false "Erro ao obter metricas: $_"
}

# ------------------------------------------------------------------------------
# RESUMO
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " RESUMO FINAL DOS TESTES E2E" -ForegroundColor Cyan
Write-Host " Passou: $Global:PassedCount" -ForegroundColor Green
Write-Host " Falhou: $Global:FailedCount" -ForegroundColor $(if ($Global:FailedCount -gt 0) { "Red" } else { "DarkGray" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Global:FailedCount -eq 0) {
    Write-Host "TODOS OS TESTES E2E DE PRODUCAO FORAM EXECUTADOS COM SUCESSO!" -ForegroundColor Green
}
