# Security Architecture

This document describes the security mechanisms implemented in the SMS Payment Checker system.

---

## Overview

The system uses a multi-layered security approach:

```
Android App                              Laravel Server
-----------                              -------------
1. Android Keystore (local)              5. API Key verification
2. EncryptedSharedPreferences            6. HMAC-SHA256 verification
3. AES-256-GCM encryption               7. AES-256-GCM decryption
4. HMAC-SHA256 signing                   8. Nonce + Timestamp validation
```

---

## Encryption (AES-256-GCM)

### Algorithm Details

| Parameter | Value |
|-----------|-------|
| Algorithm | AES-256-GCM (Authenticated Encryption) |
| Key Length | 256 bits (32 bytes) |
| IV Length | 96 bits (12 bytes) |
| Tag Length | 128 bits (16 bytes) |
| Key Derivation | First 32 bytes of secret key (zero-padded if shorter) |

### Data Format

The encrypted payload is encoded as:

```
Base64( IV [12 bytes] + Ciphertext [variable] + Auth Tag [16 bytes] )
```

### Why AES-256-GCM?

- **Authenticated encryption** - provides both confidentiality and integrity
- **Performance** - GCM mode is hardware-accelerated on modern processors
- **Standard** - widely supported across platforms (Android Cipher, PHP OpenSSL)
- **No padding oracle** - immune to padding oracle attacks (unlike CBC mode)

### Key Derivation

```
secret_key = "abc123...xyz" (64 hex chars from device registration)
aes_key = first_32_bytes(secret_key) padded with 0x00 if shorter
```

> **Note**: Both Android (`SecretKeySpec`) and PHP (`str_pad/substr`) use the same key derivation.

---

## Request Signing (HMAC-SHA256)

### Purpose

HMAC signatures ensure:
1. **Integrity** - payload has not been tampered with
2. **Authentication** - only the device with the correct secret key can sign
3. **Non-repudiation** - server can verify the request came from a legitimate device

### Signature Computation

```
signature_data = encrypted_payload + nonce + timestamp
signature = Base64( HMAC-SHA256(signature_data, secret_key) )
```

### Verification

The server uses `hash_equals()` for constant-time comparison, preventing timing attacks.

```php
$expected = base64_encode(hash_hmac('sha256', $data, $secretKey, true));
return hash_equals($expected, $signature);
```

---

## Replay Attack Prevention

### Nonce System

Each request includes a unique nonce (16 random bytes, Base64 encoded):

1. Android app generates a cryptographically random nonce
2. Nonce is included in the encrypted payload AND as a request header
3. Server checks the `sms_payment_nonces` table
4. If nonce exists: **reject** (duplicate/replay)
5. If new: **record** and continue
6. Old nonces are cleaned up after 24 hours

### Timestamp Validation

Requests must arrive within a **5-minute window** (300,000 milliseconds):

```php
$currentTime = intval(round(microtime(true) * 1000));
if (abs($currentTime - $requestTime) > 300000) {
    // Reject - request too old or too far in the future
}
```

This prevents:
- **Replay of old captured requests** (even if nonce DB was cleared)
- **Pre-computed future requests**
- **Clock skew attacks** (uses absolute difference)

---

## Device Authentication

### API Key

- **Generation**: `bin2hex(random_bytes(32))` = 64 hexadecimal characters
- **Storage**: Hashed in database, stored encrypted on device
- **Transmission**: via `X-Api-Key` header (HTTPS encrypted)
- **Scope**: identifies and authenticates the device

### Secret Key

- **Generation**: `bin2hex(random_bytes(32))` = 64 hexadecimal characters
- **Purpose**: used for AES encryption AND HMAC signing
- **Storage**:
  - Server: stored in `sms_checker_devices.secret_key` (hidden from API responses)
  - Android: stored in `EncryptedSharedPreferences` backed by Android Keystore
- **Never transmitted**: secret key is only shared once during device setup

### Device ID

- **Format**: `SMSCHK-{UUID}` (e.g., `SMSCHK-a1b2c3d4`)
- **Purpose**: additional device identification
- **Validation**: server checks `X-Device-Id` header matches the device record

---

## Local Security (Android)

### Android Keystore

The Android Keystore system protects cryptographic keys:

- Keys are stored in hardware-backed storage (TEE/Strongbox when available)
- Keys cannot be extracted from the device
- Used as the master key for EncryptedSharedPreferences

### EncryptedSharedPreferences

All sensitive data on the device is stored using `EncryptedSharedPreferences`:

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

EncryptedSharedPreferences.create(
    context,
    "secure_sms_checker_prefs",
    masterKey,
    PrefKeyEncryptionScheme.AES256_SIV,      // Key encryption
    PrefValueEncryptionScheme.AES256_GCM      // Value encryption
)
```

Stored data includes:
- API keys for each server
- Secret keys for each server
- Device ID
- Monitoring state

---

## Transport Security

### HTTPS Requirement

All API communication uses HTTPS (TLS 1.2+):

- Encrypted in transit
- Certificate validation by default (Android system trust store)
- Prevents man-in-the-middle attacks

### Request Headers

Every `/notify` request includes 5 security headers:

| Header | Purpose |
|--------|---------|
| `X-Api-Key` | Device authentication |
| `X-Device-Id` | Device identification |
| `X-Signature` | HMAC-SHA256 integrity check |
| `X-Nonce` | Replay attack prevention |
| `X-Timestamp` | Request freshness validation |

---

## Unique Amount System Security

### Purpose

The unique decimal amount system prevents amount collision:

```
Base amount: 500.00 THB
Unique amount: 500.37 THB (suffix: 37)
```

### How It Works

1. Checkout system requests a unique amount for 500.00 THB
2. Server finds an unused decimal suffix (01-99) for that base amount
3. Returns 500.37 THB to display to the customer
4. When SMS arrives with exactly 500.37, it auto-matches
5. Suffix is released after expiry (default: 30 minutes)

### Security Considerations

- **Maximum 99 concurrent amounts** per base price (configurable)
- **Automatic expiry** prevents stale reservations
- **Index-based lookup** (not unique constraint) allows suffix reuse
- **Race condition protection** via database transactions

---

## Rate Limiting

Configure in `config/smschecker.php`:

```php
'rate_limit' => env('SMSCHECKER_RATE_LIMIT', 30), // per minute
```

Apply Laravel rate limiting middleware as needed.

---

## Security Checklist

### Production Deployment

- [ ] Use HTTPS with valid SSL certificate
- [ ] Set strong, unique `SMSCHECKER_API_KEY` and `SMSCHECKER_SECRET_KEY`
- [ ] Enable `APP_DEBUG=false` in Laravel
- [ ] Configure rate limiting
- [ ] Set up log monitoring for security warnings
- [ ] Schedule `smschecker:cleanup` to run every 5 minutes
- [ ] Review device list regularly (`smschecker:status`)
- [ ] Keep PHP, Laravel, and Android dependencies updated

### Device Security

- [ ] Keep Android app updated
- [ ] Use device with hardware-backed keystore
- [ ] Do not root/jailbreak the device
- [ ] Keep screen lock enabled
- [ ] Restrict SMS permissions to this app only

### Network Security

- [ ] Use HTTPS only (no HTTP fallback)
- [ ] Consider IP whitelisting for the API
- [ ] Monitor for unusual request patterns
- [ ] Set up alerts for signature verification failures

---

## Threat Model

| Threat | Mitigation |
|--------|------------|
| SMS interception | AES-256-GCM encryption of payload |
| Request tampering | HMAC-SHA256 signature verification |
| Replay attacks | Nonce + Timestamp validation |
| Credential theft (device) | EncryptedSharedPreferences + Android Keystore |
| Credential theft (server) | `hidden` attributes on model, HTTPS |
| Brute force | Rate limiting, 64-char hex keys |
| Timing attacks | `hash_equals()` and constant-time comparison |
| Amount collision | Unique decimal suffix system |
| Man-in-the-middle | HTTPS/TLS 1.2+ |
| Device compromise | Device status management (can block remotely) |

---

## Incident Response

If a device is compromised:

1. **Block the device** immediately:
   ```bash
   # In database
   UPDATE sms_checker_devices SET status = 'blocked' WHERE device_id = 'SMSCHK-xxxx';
   ```

2. **Rotate keys** - generate new API key and secret key

3. **Review logs** - check `sms_payment_notifications` for suspicious activity

4. **Notify stakeholders** - inform relevant parties about potential unauthorized transactions
