#!/bin/bash
set -euo pipefail

# ──────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────
KC_URL="${KC_URL:-http://localhost:8081}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin}"
REALM_NAME="${REALM_NAME:-skroutz-scraper}"
USER_NAME="${USER_NAME:-admin}"
USER_PASS="${USER_PASS:-admin}"
ROLE_NAME="${ROLE_NAME:-SUPER_ADMIN}"
CLIENT_NAME="${CLIENT_NAME:-skroutz-scraper-client}"

# ──────────────────────────────────────────────────────────────
# Wait for Keycloak to be ready
# ──────────────────────────────────────────────────────────────
echo "⏳ Waiting for Keycloak to be ready..."
for i in {1..30}; do
  if curl -s -f -o /dev/null "${KC_URL}/realms/master" 2>/dev/null; then
    echo "✅ Keycloak is ready."
    break
  fi
  sleep 2
done

# ──────────────────────────────────────────────────────────────
# Get Master Admin Token (jq-free)
# ──────────────────────────────────────────────────────────────
echo "🔑 Authenticating as Master Admin..."
TOKEN=$(curl -s -f -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "username=${KC_ADMIN_USER}" \
  -d "password=${KC_ADMIN_PASS}" \
  -d "client_id=admin-cli" | awk -F'"' '/access_token/{print $4}')

if [ -z "$TOKEN" ]; then
  echo "❌ Failed to get master admin token. Check credentials & Keycloak status."
  exit 1
fi
echo "✅ Admin token acquired."

# ──────────────────────────────────────────────────────────────
# Create Realm (Idempotent)
# ──────────────────────────────────────────────────────────────
echo "🌍 Checking realm: ${REALM_NAME}..."
if curl -s -f -o /dev/null -H "Authorization: Bearer ${TOKEN}" "${KC_URL}/admin/realms/${REALM_NAME}"; then
  echo "✅ Realm already exists."
else
  echo "🌍 Creating realm: ${REALM_NAME}..."
  curl -s -f -X POST "${KC_URL}/admin/realms" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d "{\"realm\": \"${REALM_NAME}\", \"enabled\": true}"
  echo "✅ Realm created."
fi

# ──────────────────────────────────────────────────────────────
# Disable Realm-Level Default Required Actions
# ──────────────────────────────────────────────────────────────
echo "⚙️ Disabling default required actions in realm ${REALM_NAME}..."
for ACTION in "VERIFY_EMAIL" "UPDATE_PASSWORD" "CONFIGURE_TOTP" "terms_and_conditions" "UPDATE_PROFILE"; do
  curl -s -X PUT "${KC_URL}/admin/realms/${REALM_NAME}/authentication/required-actions/${ACTION}" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d "{\"alias\": \"${ACTION}\", \"enabled\": false, \"defaultAction\": false}" >/dev/null || true
done
echo "✅ Realm default actions disabled."

# ──────────────────────────────────────────────────────────────
# Create Application Client (Idempotent)
# ──────────────────────────────────────────────────────────────
echo "📦 Checking client: ${CLIENT_NAME}..."
APP_CLIENT_JSON=$(curl -s -f -H "Authorization: Bearer ${TOKEN}" "${KC_URL}/admin/realms/${REALM_NAME}/clients?clientId=${CLIENT_NAME}")
APP_CLIENT_ID=$(echo "$APP_CLIENT_JSON" | awk -F'"' '/"id"/{print $4}')

if [ -z "$APP_CLIENT_ID" ]; then
  echo "📦 Creating client: ${CLIENT_NAME}..."
  LOCATION=$(curl -s -f -X POST "${KC_URL}/admin/realms/${REALM_NAME}/clients" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d "{
      \"clientId\": \"${CLIENT_NAME}\",
      \"enabled\": true,
      \"publicClient\": true,
      \"directAccessGrantsEnabled\": true,
      \"standardFlowEnabled\": false,
      \"implicitFlowEnabled\": false,
      \"attributes\": {
        \"client.use.lightweight.access.token.enabled\": \"false\"
      }
    }" -w '%{redirect_url}' -o /dev/null)
  APP_CLIENT_ID=$(echo "$LOCATION" | awk -F'/' '{print $NF}')
  echo "✅ Client created (ID: ${APP_CLIENT_ID})."
else
  echo "✅ Client already exists (ID: ${APP_CLIENT_ID})."
fi

# ──────────────────────────────────────────────────────────────
# Create Client Role for app client (for resource_access in JWT)
# ──────────────────────────────────────────────────────────────
echo "🛡️ Creating client role ${ROLE_NAME} for ${CLIENT_NAME}..."
CLIENT_ROLE_JSON=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${KC_URL}/admin/realms/${REALM_NAME}/clients/${APP_CLIENT_ID}/roles/${ROLE_NAME}" || echo "")
CLIENT_ROLE_EXISTS=$(echo "$CLIENT_ROLE_JSON" | awk -F'"' '/"name"/{print $4}')
if [ "$CLIENT_ROLE_EXISTS" != "${ROLE_NAME}" ]; then
  curl -s -f -X POST "${KC_URL}/admin/realms/${REALM_NAME}/clients/${APP_CLIENT_ID}/roles" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d "{\"name\": \"${ROLE_NAME}\"}"
  echo "✅ Client role created."
else
  echo "✅ Client role already exists."
fi
CLIENT_ROLE_JSON=$(curl -s -f -H "Authorization: Bearer ${TOKEN}" "${KC_URL}/admin/realms/${REALM_NAME}/clients/${APP_CLIENT_ID}/roles/${ROLE_NAME}")
CLIENT_ROLE_ID=$(echo "$CLIENT_ROLE_JSON" | awk -F'"' '/"id"/{print $4}')

# ──────────────────────────────────────────────────────────────
# Clean & Create Fully Configured User
# ──────────────────────────────────────────────────────────────
echo "👤 Checking existing user: ${USER_NAME}..."
EXISTING_USER_ID=$(curl -s -f -H "Authorization: Bearer ${TOKEN}" "${KC_URL}/admin/realms/${REALM_NAME}/users?username=${USER_NAME}" | awk -F'"' '/"id"/{print $4}')

if [ -n "$EXISTING_USER_ID" ]; then
  echo "🧹 Deleting existing user to ensure clean state..."
  curl -s -f -X DELETE "${KC_URL}/admin/realms/${REALM_NAME}/users/${EXISTING_USER_ID}" \
    -H "Authorization: Bearer ${TOKEN}"
fi

echo "👤 Creating user with complete profile & non-temporary credentials..."
curl -s -f -X POST "${KC_URL}/admin/realms/${REALM_NAME}/users" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "{
    \"username\": \"${USER_NAME}\",
    \"firstName\": \"Admin\",
    \"lastName\": \"User\",
    \"email\": \"${USER_NAME}@${REALM_NAME}.local\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"requiredActions\": [],
    \"credentials\": [
      {
        \"type\": \"password\",
        \"value\": \"${USER_PASS}\",
        \"temporary\": false
      }
    ]
  }"

USER_ID=$(curl -s -f -H "Authorization: Bearer ${TOKEN}" "${KC_URL}/admin/realms/${REALM_NAME}/users?username=${USER_NAME}" | awk -F'"' '/"id"/{print $4}')

echo "✅ User created successfully (ID: ${USER_ID})"

# ──────────────────────────────────────────────────────────────
# Assign Client Role to User (for resource_access in JWT)
# ──────────────────────────────────────────────────────────────
echo "🔗 Assigning ${ROLE_NAME} client role to user ${USER_NAME}..."
curl -s -f -X POST "${KC_URL}/admin/realms/${REALM_NAME}/users/${USER_ID}/role-mappings/clients/${APP_CLIENT_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "[{\"id\": \"${CLIENT_ROLE_ID}\", \"name\": \"${ROLE_NAME}\"}]"

echo "✅ Client role assigned successfully."

# ──────────────────────────────────────────────────────────────
# Debug Check User Required Actions
# ──────────────────────────────────────────────────────────────
USER_DETAILS=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${KC_URL}/admin/realms/${REALM_NAME}/users/${USER_ID}")
echo "🔍 User details check from Keycloak DB:"
echo "$USER_DETAILS"

# ──────────────────────────────────────────────────────────────
# Test Token Acquisition for the User
# ──────────────────────────────────────────────────────────────
echo "🧪 Testing token generation for user '${USER_NAME}' in realm '${REALM_NAME}'..."

USER_TOKEN_RESPONSE=$(curl -s -X POST "${KC_URL}/realms/${REALM_NAME}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_NAME}" \
  -d "username=${USER_NAME}" \
  -d "password=${USER_PASS}")

USER_TOKEN=$(echo "$USER_TOKEN_RESPONSE" | awk -F'"' '/access_token/{print $4}')

if [ -n "$USER_TOKEN" ]; then
  echo "🎉 SUCCESS: Token successfully acquired for user '${USER_NAME}'!"
  echo "🔑 Access Token Preview: ${USER_TOKEN:0:30}..."

  # Decode and print token payload for verification
  PAYLOAD=$(echo "$USER_TOKEN" | cut -d'.' -f2)
  PADDING=$(( 4 - ${#PAYLOAD} % 4 ))
  DECODED=$(echo "${PAYLOAD}$(printf '=%.0s' $(seq 1 $PADDING))" | base64 -d 2>/dev/null || echo "$PAYLOAD==" | base64 -d 2>/dev/null || echo "DECODE_FAILED")
  echo "🔍 Token payload:"
  echo "$DECODED"
else
  echo "❌ Verification Failed. Keycloak Response:"
  echo "$USER_TOKEN_RESPONSE"
  exit 1
fi

echo "🎉 Keycloak setup and verification complete!"
