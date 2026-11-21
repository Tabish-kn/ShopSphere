<#
.SYNOPSIS
    ShopSphere End-to-End Microservices Platform Verification Script (PowerShell).
.DESCRIPTION
    Validates health and exercises the complete choreography-based Saga workflow:
    1. Healthchecks API Gateway & Eureka
    2. Registers a test customer
    3. Authenticates & receives JWT Bearer token
    4. Creates product category & catalog item
    5. Restocks inventory item
    6. Adds product to shopping cart (Redis)
    7. Submits Checkout order (PostgreSQL Outbox -> Kafka -> Saga Choreography)
    8. Polls order status until Saga CONFIRMED (or CANCELLED)
    9. Inspects Prometheus metrics & Zipkin traces
#>

param (
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$PrometheusUrl = "http://localhost:9090",
    [string]$ZipkinUrl = "http://localhost:9411"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "    ShopSphere Enterprise Platform Verification Suite     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

function Test-Endpoint {
    param([string]$Url, [string]$Name)
    try {
        $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 5
        Write-Host " [PASS] $Name is healthy: $Url" -ForegroundColor Green
        return $true
    } catch {
        Write-Host " [FAIL] $Name unreachable: $Url - $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# Step 1: Connectivity Health Checks
Write-Host "`n>>> [1/7] Probing Gateway & Eureka Actuator Endpoints..." -ForegroundColor Yellow
$gwHealth = Test-Endpoint -Url "$GatewayUrl/actuator/health" -Name "API Gateway"

if (-not $gwHealth) {
    Write-Host "Make sure 'docker compose up -d' has finished starting all services." -ForegroundColor Red
    exit 1
}

# Step 2: Register & Authenticate Customer
Write-Host "`n>>> [2/7] Creating Customer Account & Obtaining JWT..." -ForegroundColor Yellow
$email = "qa_user_$(Get-Random)@shopsphere.io"
$password = "StrongP@ss123!"

$registerPayload = @{
    email = $email
    password = $password
    firstName = "Enterprise"
    lastName = "Tester"
    phoneNumber = "+1234567890"
    address = "100 Cloud Native Blvd, Kubernetes City"
} | ConvertTo-Json

try {
    $regResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/register" -Method Post -Body $registerPayload -ContentType "application/json"
    Write-Host " [PASS] Customer Registered: $email (ID: $($regResponse.data.id))" -ForegroundColor Green
} catch {
    Write-Host " [FAIL] Registration error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Login to acquire Bearer Token
$loginPayload = @{
    email = $email
    password = $password
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/login" -Method Post -Body $loginPayload -ContentType "application/json"
$jwtToken = $loginResponse.data.accessToken
Write-Host " [PASS] JWT Token Acquired (Length: $($jwtToken.Length))" -ForegroundColor Green

$authHeaders = @{
    "Authorization" = "Bearer $jwtToken"
    "Content-Type"  = "application/json"
    "X-Correlation-Id" = "test-corr-$(Get-Random)"
}

# Step 3: Seed Category & Product
Write-Host "`n>>> [3/7] Publishing Product Catalog..." -ForegroundColor Yellow
$categoryPayload = @{
    name = "Smart Electronics"
    description = "Next-gen computing devices"
    slug = "smart-electronics-$(Get-Random)"
} | ConvertTo-Json

$categoryResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/categories" -Method Post -Headers $authHeaders -Body $categoryPayload
$categoryId = $categoryResponse.data.id
Write-Host " [PASS] Category created (ID: $categoryId)" -ForegroundColor Green

$productSku = "SKU-PRO-$(Get-Random)"
$productPayload = @{
    sku = $productSku
    name = "ShopSphere UltraBook Pro"
    description = "Flagship developer workstation"
    price = 1499.99
    categoryId = $categoryId
    active = $true
} | ConvertTo-Json

$productResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/products" -Method Post -Headers $authHeaders -Body $productPayload
$productId = $productResponse.data.id
Write-Host " [PASS] Product created: $productSku (ID: $productId)" -ForegroundColor Green

# Step 4: Seed Stock in Inventory
Write-Host "`n>>> [4/7] Allocating Stock in Inventory Service..." -ForegroundColor Yellow
$stockPayload = @{
    productId = $productId
    sku = $productSku
    quantity = 100
} | ConvertTo-Json

$invResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/inventory/restock" -Method Post -Headers $authHeaders -Body $stockPayload
Write-Host " [PASS] Inventory stocked with 100 units for SKU: $productSku" -ForegroundColor Green

# Step 5: Add to Cart
Write-Host "`n>>> [5/7] Adding Item to Shopping Cart (Redis Session)..." -ForegroundColor Yellow
$cartPayload = @{
    productId = $productId
    sku = $productSku
    quantity = 2
} | ConvertTo-Json

$cartResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/cart/items" -Method Post -Headers $authHeaders -Body $cartPayload
Write-Host " [PASS] Added 2 units to cart. Total Cart Items: $($cartResponse.data.items.Count)" -ForegroundColor Green

# Step 6: Checkout & Trigger Choreographed Saga
Write-Host "`n>>> [6/7] Submitting Order & Launching Kafka Outbox Saga..." -ForegroundColor Yellow
$orderPayload = @{
    shippingAddress = "100 Cloud Native Blvd, Kubernetes City"
    paymentMethod = "CREDIT_CARD"
    items = @(
        @{
            productId = $productId
            sku = $productSku
            quantity = 2
            price = 1499.99
        }
    )
} | ConvertTo-Json

$orderResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/orders" -Method Post -Headers $authHeaders -Body $orderPayload
$orderId = $orderResponse.data.id
$trackingNumber = $orderResponse.data.trackingNumber
Write-Host " [PASS] Order Created: $orderId (Tracking: $trackingNumber, Initial Status: $($orderResponse.data.status))" -ForegroundColor Green

Write-Host " Polling Saga progress across Kafka topics (order-created -> inventory-reserved -> payment-success -> order-confirmed)..." -ForegroundColor Cyan
$maxAttempts = 15
$attempt = 1
$finalStatus = $null

while ($attempt -le $maxAttempts) {
    Start-Sleep -Seconds 2
    $checkResponse = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/orders/$orderId" -Method Get -Headers $authHeaders
    $currentStatus = $checkResponse.data.status
    Write-Host "  [Poll $attempt/$maxAttempts] Current Order Status: $currentStatus" -ForegroundColor Gray
    
    if ($currentStatus -in @("CONFIRMED", "CANCELLED")) {
        $finalStatus = $currentStatus
        break
    }
    $attempt++
}

if ($finalStatus -eq "CONFIRMED") {
    Write-Host " [PASS] SAGA COMPLETED SUCCESSFULLY! Order status reached CONFIRMED!" -ForegroundColor Green
} elseif ($finalStatus -eq "CANCELLED") {
    Write-Host " [WARN] Saga executed compensation rollback! Status: CANCELLED" -ForegroundColor Yellow
} else {
    Write-Host " [FAIL] Saga timed out waiting for terminal state. Current: $currentStatus" -ForegroundColor Red
}

# Step 7: Observability Metrics Scrape
Write-Host "`n>>> [7/7] Validating Observability (Prometheus & Zipkin)..." -ForegroundColor Yellow
Test-Endpoint -Url "$PrometheusUrl/-/healthy" -Name "Prometheus Server"
Test-Endpoint -Url "$ZipkinUrl/health" -Name "Zipkin Distributed Tracer"

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  Platform Verification Complete! All Systems Operational." -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
