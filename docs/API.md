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

## Signature Algorithm

### How to compute X-Signature

```
signature_data = encrypted_payload + nonce + timestamp
signature = Base64(HMAC-SHA256(signature_data, secret_key))
```

### PHP Example

```php
$signatureData = $encryptedData . $nonce . $timestamp;
$signature = base64_encode(hash_hmac('sha256', $signatureData, $secretKey, true));
```

### Kotlin Example

```kotlin
val signatureData = "$encryptedData$nonce$timestamp"
val mac = Mac.getInstance("HmacSHA256")
mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
val signature = Base64.encodeToString(mac.doFinal(signatureData.toByteArray()), Base64.NO_WRAP)
```
