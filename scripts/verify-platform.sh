#!/usr/bin/env bash
# ==============================================================================
# ShopSphere End-to-End Microservices Platform Verification Script (Bash)
# ==============================================================================
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"

echo "=========================================================="
echo "    ShopSphere Enterprise Platform Verification Suite     "
echo "=========================================================="

echo -e "\n>>> [1/7] Probing Gateway Actuator Health..."
GATEWAY_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/actuator/health" || echo "000")
if [ "$GATEWAY_HEALTH" != "200" ]; then
  echo "[-] API Gateway is not responding (HTTP $GATEWAY_HEALTH). Verify 'docker compose up -d' is running."
  exit 1
fi
echo "[+] API Gateway is healthy."

RANDOM_ID=$((RANDOM % 90000 + 10000))
EMAIL="qa_user_${RANDOM_ID}@shopsphere.io"
PASSWORD="StrongP@ss123!"

echo -e "\n>>> [2/7] Registering & Authenticating User ($EMAIL)..."
REGISTER_PAYLOAD=$(cat <<EOF
{
  "email": "$EMAIL",
  "password": "$PASSWORD",
  "firstName": "Cloud",
  "lastName": "Tester",
  "phoneNumber": "+18005550199",
  "address": "42 Microservice Way, Cloud City"
}
EOF
)

curl -s -X POST "$GATEWAY_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "$REGISTER_PAYLOAD" > /dev/null

LOGIN_PAYLOAD=$(cat <<EOF
{
  "email": "$EMAIL",
  "password": "$PASSWORD"
}
EOF
)

LOGIN_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "$LOGIN_PAYLOAD")

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*' | grep -o '[^"]*$')

if [ -z "$ACCESS_TOKEN" ]; then
  echo "[-] Failed to obtain access token from response: $LOGIN_RESPONSE"
  exit 1
fi
echo "[+] Access Token acquired."

AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"
CORR_HEADER="X-Correlation-Id: e2e-verify-$RANDOM_ID"

echo -e "\n>>> [3/7] Creating Product & Category..."
CATEGORY_PAYLOAD=$(cat <<EOF
{
  "name": "Cloud Hardware $RANDOM_ID",
  "description": "Enterprise Grade Servers",
  "slug": "cloud-hw-$RANDOM_ID"
}
EOF
)
CAT_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/categories" \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -H "$CORR_HEADER" \
  -d "$CATEGORY_PAYLOAD")
CATEGORY_ID=$(echo "$CAT_RESP" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

SKU="SKU-SRV-$RANDOM_ID"
PRODUCT_PAYLOAD=$(cat <<EOF
{
  "sku": "$SKU",
  "name": "Edge Inference Server $RANDOM_ID",
  "description": "High throughput low latency edge compute",
  "price": 2499.50,
  "categoryId": $CATEGORY_ID,
  "active": true
}
EOF
)
PROD_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/products" \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -H "$CORR_HEADER" \
  -d "$PRODUCT_PAYLOAD")
PRODUCT_ID=$(echo "$PROD_RESP" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "[+] Created Category ($CATEGORY_ID) and Product $SKU ($PRODUCT_ID)"

echo -e "\n>>> [4/7] Restocking Inventory..."
STOCK_PAYLOAD=$(cat <<EOF
{
  "productId": $PRODUCT_ID,
  "sku": "$SKU",
  "quantity": 50
}
EOF
)
curl -s -X POST "$GATEWAY_URL/api/v1/inventory/restock" \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -H "$CORR_HEADER" \
  -d "$STOCK_PAYLOAD" > /dev/null
echo "[+] Stocked 50 units for $SKU"

echo -e "\n>>> [5/7] Adding Item to Redis Cart..."
CART_PAYLOAD=$(cat <<EOF
{
  "productId": $PRODUCT_ID,
  "sku": "$SKU",
  "quantity": 2
}
EOF
)
curl -s -X POST "$GATEWAY_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -H "$CORR_HEADER" \
  -d "$CART_PAYLOAD" > /dev/null
echo "[+] Item added to cart."

echo -e "\n>>> [6/7] Submitting Order & Launching Kafka Saga..."
ORDER_PAYLOAD=$(cat <<EOF
{
  "shippingAddress": "42 Microservice Way, Cloud City",
  "paymentMethod": "CREDIT_CARD",
  "items": [
    {
      "productId": $PRODUCT_ID,
      "sku": "$SKU",
      "quantity": 2,
      "price": 2499.50
    }
  ]
}
EOF
)
ORDER_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -H "$CORR_HEADER" \
  -d "$ORDER_PAYLOAD")
ORDER_ID=$(echo "$ORDER_RESP" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "[+] Order placed (ID: $ORDER_ID). Polling Saga status..."

STATUS="PENDING"
for i in {1..15}; do
  sleep 2
  STATUS_RESP=$(curl -s -X GET "$GATEWAY_URL/api/v1/orders/$ORDER_ID" \
    -H "$AUTH_HEADER" \
    -H "$CORR_HEADER")
  STATUS=$(echo "$STATUS_RESP" | grep -o '"status":"[^"]*' | head -1 | grep -o '[^"]*$')
  echo "  [Poll $i/15] Current Saga Status: $STATUS"
  if [ "$STATUS" = "CONFIRMED" ] || [ "$STATUS" = "CANCELLED" ]; then
    break
  fi
done

if [ "$STATUS" = "CONFIRMED" ]; then
  echo "[+] SUCCESS: Saga executed cleanly and order reached CONFIRMED!"
else
  echo "[-] Terminal status: $STATUS"
fi

echo -e "\n>>> [7/7] Checking Observability Endpoints..."
curl -s -o /dev/null -w "[+] Prometheus HTTP %{http_code}\n" "$PROMETHEUS_URL/-/healthy" || true
curl -s -o /dev/null -w "[+] Zipkin HTTP %{http_code}\n" "$ZIPKIN_URL/health" || true

echo "=========================================================="
echo "  Platform Verification Complete!"
echo "=========================================================="
