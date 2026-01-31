# API Reference

**Base URL**: `https://your-domain.com/api/v1/sms-payment`

---

## Authentication

### Device Authentication (Android App → Server)

All device endpoints require the `X-Api-Key` header:

```
X-Api-Key: {your_api_key}
X-Device-Id: {device_id}
```

### User Authentication (Web/Admin)

Admin endpoints use Laravel Sanctum:

```
Authorization: Bearer {sanctum_token}
```

---

## Device Endpoints

### POST /notify

Send an encrypted SMS payment notification.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |
| `X-Signature` | Yes | HMAC-SHA256 signature |
| `X-Nonce` | Yes | Unique request nonce |
| `X-Timestamp` | Yes | Request timestamp (milliseconds) |

**Body:**

```json
{
  "data": "Base64-encoded-AES-256-GCM-encrypted-payload"
}
```

**Decrypted Payload Format:**

```json
{
  "bank": "KBANK",
  "type": "credit",
  "amount": "500.37",
  "account_number": "1234",
  "sender_or_receiver": "นายทดสอบ",
  "reference_number": "REF12345",
  "sms_timestamp": 1706540000000,
  "device_id": "SMSCHK-ABCD1234",
  "nonce": "unique-nonce-base64"
}
```

**Response (Success):**

```json
{
  "success": true,
  "message": "Payment matched and confirmed",
  "data": {
    "notification_id": 42,
    "status": "matched",
    "matched": true,
    "matched_transaction_id": 156
  }
}
```

**Response (Recorded but no match):**

```json
{
  "success": true,
  "message": "Notification recorded",
  "data": {
    "notification_id": 43,
    "status": "pending",
    "matched": false,
    "matched_transaction_id": null
  }
}
```

**Error Responses:**

| Code | Message | Cause |
|------|---------|-------|
| 400 | Missing required security headers | Missing X-Signature, X-Nonce, or X-Timestamp |
| 400 | Request timestamp expired | Timestamp older than 5 minutes |
| 400 | No payload data | Empty request body |
| 400 | Failed to decrypt payload | Wrong secret key or corrupted data |
| 400 | Duplicate request | Nonce already used (replay attack) |
| 401 | Invalid signature | HMAC verification failed |
| 401 | Invalid API key | API key not found |
| 403 | Device is inactive/blocked | Device suspended |
| 422 | Invalid payload data | Validation errors in decrypted data |

---

### GET /status

Check device status and pending notification count.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | No | Device identifier |

**Response:**

```json
{
  "success": true,
  "status": "active",
  "pending_count": 3,
  "message": null
}
```

---

### POST /register-device

Update device information.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |

**Body:**

```json
{
  "device_id": "SMSCHK-ABCD1234",
  "device_name": "Samsung Galaxy S24",
  "platform": "android",
  "app_version": "1.0.0"
}
```

**Response:**

```json
{
  "success": true,
  "message": "Device registered successfully"
}
```

---

### GET /orders

Get a paginated list of orders for the device.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |

**Query Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `status` | string | Filter by approval status: `pending`, `approved`, `rejected` |
| `date_from` | string | Filter orders created on or after this date (ISO 8601) |
| `date_to` | string | Filter orders created on or before this date (ISO 8601) |
| `page` | int | Page number (default: 1) |
| `per_page` | int | Items per page (default: 20) |

**Response:**

```json
{
  "success": true,
  "data": {
    "data": [
      {
        "id": 1,
        "notification_id": 42,
        "matched_transaction_id": 156,
        "device_id": "SMSCHK-ABCD1234",
        "approval_status": "pending",
        "confidence": 0.95,
        "approved_by": null,
        "approved_at": null,
        "rejected_at": null,
        "rejection_reason": null,
        "order_details_json": {
          "order_number": "ORD-2025-001",
          "product_name": "Premium Widget"
        },
        "synced_version": 3,
        "created_at": "2025-01-29T14:30:05.000000Z",
        "updated_at": "2025-01-29T14:30:05.000000Z",
        "notification": {
          "id": 42,
          "bank": "KBANK",
          "type": "credit",
          "amount": "500.37",
          "sms_timestamp": "2025-01-29T14:30:00.000000Z",
          "sender_or_receiver": "นายทดสอบ"
        }
      }
    ],
    "current_page": 1,
    "last_page": 3,
    "total": 52
  }
}
```

---

### POST /orders/{id}/approve

Approve a specific order.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |

**URL Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | int | Order ID to approve |

**Response:**

```json
{
  "success": true,
  "message": "Order approved"
}
```

---

### POST /orders/{id}/reject

Reject a specific order with a reason.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |

**URL Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | int | Order ID to reject |

**Body:**

```json
{
  "reason": "Amount does not match expected payment"
}
```

**Response:**

```json
{
  "success": true,
  "message": "Order rejected"
}
```

---

### POST /orders/bulk-approve

Approve multiple orders at once.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |

**Body:**

```json
{
  "ids": [1, 2, 3]
}
```

**Response:**

```json
{
  "success": true,
  "message": "Orders approved"
}
```

---

### GET /orders/sync

Sync orders that have changed since a given version number. Used for incremental synchronization between the Android app and the server.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |

**Query Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `since_version` | int | Return orders with `synced_version` greater than this value |

**Response:**

```json
{
  "success": true,
  "data": {
    "orders": [
      {
        "id": 1,
        "notification_id": 42,
        "matched_transaction_id": 156,
        "device_id": "SMSCHK-ABCD1234",
        "approval_status": "approved",
        "confidence": 0.95,
        "approved_by": "device",
        "approved_at": "2025-01-29T15:00:00.000000Z",
        "rejected_at": null,
        "rejection_reason": null,
        "order_details_json": {
          "order_number": "ORD-2025-001",
          "product_name": "Premium Widget"
        },
        "synced_version": 5,
        "created_at": "2025-01-29T14:30:05.000000Z",
        "updated_at": "2025-01-29T15:00:00.000000Z",
        "notification": {
          "id": 42,
          "bank": "KBANK",
          "type": "credit",
          "amount": "500.37",
          "sms_timestamp": "2025-01-29T14:30:00.000000Z",
          "sender_or_receiver": "นายทดสอบ"
        }
      }
    ],
    "latest_version": 5
  }
}
```

---

### GET /device-settings

Get the current device settings.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |

**Response:**

```json
{
  "success": true,
  "data": {
    "approval_mode": "auto"
  }
}
```

---

### PUT /device-settings

Update the device settings.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-Api-Key` | Yes | Device API key |
| `X-Device-Id` | Yes | Device identifier |

**Body:**

```json
{
  "approval_mode": "auto"
}
```

| Field | Type | Values | Description |
|-------|------|--------|-------------|
| `approval_mode` | string | `auto`, `manual` | Whether orders are auto-approved or require manual review |

**Response:**

```json
{
  "success": true,
  "data": {
    "approval_mode": "auto"
  }
}
```

---

## Admin Endpoints

### POST /generate-amount

Generate a unique payment amount for checkout.

**Authentication:** Bearer token (Sanctum)

**Body:**

```json
{
  "base_amount": 500.00,
  "transaction_id": 156,
  "transaction_type": "order",
  "expiry_minutes": 30
}
```

**Response:**

```json
{
  "success": true,
  "message": "Unique amount generated",
  "data": {
    "base_amount": "500.00",
    "unique_amount": "500.37",
    "expires_at": "2025-01-29T15:30:00+07:00",
    "display_amount": "฿500.37"
  }
}
```

**Error (409 - All suffixes in use):**

```json
{
  "success": false,
  "message": "Unable to generate unique amount. Too many pending transactions at this price."
}
```

---

### GET /notifications

View notification history with filtering.

**Authentication:** Bearer token (Sanctum)

**Query Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `status` | string | Filter: pending, matched, confirmed, rejected, expired |
| `bank` | string | Filter by bank code: KBANK, SCB, etc. |
| `type` | string | Filter: credit, debit |
| `per_page` | int | Items per page (default: 20) |
| `page` | int | Page number |

**Response:**

```json
{
  "success": true,
  "data": {
    "data": [
      {
        "id": 42,
        "bank": "KBANK",
        "type": "credit",
        "amount": "500.37",
        "account_number": "1234",
        "sender_or_receiver": "นายทดสอบ",
        "reference_number": "REF12345",
        "sms_timestamp": "2025-01-29T14:30:00.000000Z",
        "device_id": "SMSCHK-ABCD1234",
        "status": "matched",
        "matched_transaction_id": 156,
        "created_at": "2025-01-29T14:30:05.000000Z"
      }
    ],
    "current_page": 1,
    "per_page": 20,
    "total": 42
  }
}
```

---

### GET /dashboard-stats

Get dashboard statistics for a given time period.

**Authentication:** Bearer token (Sanctum)

**Query Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `days` | int | Number of days to include in the statistics (default: 7) |

**Response:**

```json
{
  "success": true,
  "data": {
    "total_orders": 120,
    "auto_approved": 85,
    "manually_approved": 20,
    "pending_review": 10,
    "rejected": 5,
    "total_amount": "45230.50",
    "daily_breakdown": [
      {
        "date": "2025-01-29",
        "count": 18,
        "approved": 15,
        "rejected": 1,
        "amount": "7250.00"
      },
      {
        "date": "2025-01-28",
        "count": 22,
        "approved": 20,
        "rejected": 0,
        "amount": "8100.75"
      }
    ]
  }
}
```

---

## QR Code / Web Endpoints

These endpoints are served outside the API prefix and are used for device setup via QR code scanning.

**Base URL:** `https://your-domain.com`

### GET /smschecker/device/{device}/qr

Render the QR code setup page for a specific device. This page displays a scannable QR code that the Android app can use to auto-configure its server connection.

**Authentication:** Auth middleware (web session)

**URL Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `device` | string | Device identifier or slug |

**Response:** HTML page containing a QR code image.

---

### GET /smschecker/device/{device}/qr.json

Return the device configuration as JSON. This is the data encoded in the QR code.

**Authentication:** Auth middleware (web session)

**URL Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `device` | string | Device identifier or slug |

**Response:**

```json
{
  "type": "smschecker_config",
  "version": 1,
  "url": "https://your-domain.com",
  "apiKey": "device_api_key_here",
  "secretKey": "device_secret_key_here",
  "deviceName": "Device Name"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `smschecker_config` — used by the app to validate the QR payload |
| `version` | int | Config schema version |
| `url` | string | Server base URL the app should connect to |
| `apiKey` | string | API key for device authentication |
| `secretKey` | string | Secret key for HMAC signing and AES encryption |
| `deviceName` | string | Human-readable device name |

---

## Signature Algorithm

### Key Derivation

Both encryption and HMAC use keys derived from the device secret via PBKDF2. The secret key is **never used directly**.

```
encryption_key = PBKDF2(secret, "thaiprompt-smschecker-v1:encryption", 100000, 32)
hmac_key       = PBKDF2(secret, "thaiprompt-smschecker-v1:hmac-signing", 100000, 32)
```

See [SECURITY.md](SECURITY.md) for full PBKDF2 parameters.

### How to compute X-Signature

```
hmac_key = PBKDF2(secret_key, "thaiprompt-smschecker-v1:hmac-signing", 100000, 32)
signature_data = encrypted_payload + nonce + timestamp
signature = Base64(HMAC-SHA256(signature_data, hmac_key))
```

### PHP Example

```php
// Derive HMAC key
$hmacKey = hash_pbkdf2('sha256', $secretKey, 'thaiprompt-smschecker-v1:hmac-signing', 100000, 32, true);

$signatureData = $encryptedData . $nonce . $timestamp;
$signature = base64_encode(hash_hmac('sha256', $signatureData, $hmacKey, true));
```

### Kotlin Example

```kotlin
// Derive HMAC key
val salt = "thaiprompt-smschecker-v1:hmac-signing".toByteArray(Charsets.UTF_8)
val spec = PBEKeySpec(secretKey.toCharArray(), salt, 100_000, 256)
val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
val hmacKey = SecretKeySpec(factory.generateSecret(spec).encoded, "HmacSHA256")
spec.clearPassword()

val signatureData = "$encryptedData$nonce$timestamp"
val mac = Mac.getInstance("HmacSHA256")
mac.init(hmacKey)
val signature = Base64.encodeToString(mac.doFinal(signatureData.toByteArray()), Base64.NO_WRAP)
```
