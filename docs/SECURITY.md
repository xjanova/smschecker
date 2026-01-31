# Security Architecture

This document describes the security mechanisms implemented in the SMS Payment Checker system.

---

## Overview

The system uses a multi-layered security approach:

```
Android App                              Laravel Server
-----------                              -------------
1. Android Keystore (local)              6. API Key verification
2. EncryptedSharedPreferences            7. HMAC-SHA256 verification (derived HMAC key)
3. PBKDF2 key derivation (100K iter)     8. PBKDF2 key derivation (100K iter)
4. AES-256-GCM encryption               9. AES-256-GCM decryption
5. HMAC-SHA256 signing (derived key)    10. Nonce + Timestamp validation
                                        11. Pessimistic locking on amount match
                                        12. Bank account verification
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
| Key Derivation | PBKDF2WithHmacSHA256 (100K iterations, context-specific salt) |

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

### Key Derivation (PBKDF2)

Secret keys are **never used directly** for encryption or signing. Both Android and Laravel derive purpose-specific keys using PBKDF2:

| Parameter | Value |
|-----------|-------|
| Algorithm | PBKDF2WithHmacSHA256 |
| Iterations | 100,000 |
| Output Key Length | 256 bits (32 bytes) |
| Salt (encryption) | `thaiprompt-smschecker-v1:encryption` |
| Salt (HMAC) | `thaiprompt-smschecker-v1:hmac-signing` |

This produces **two separate keys** from the same secret:
- **Encryption key** -- used for AES-256-GCM encrypt/decrypt
- **HMAC key** -- used for HMAC-SHA256 signing/verification

```
encryption_key = PBKDF2(secret, "thaiprompt-smschecker-v1:encryption", 100000, 32)
hmac_key       = PBKDF2(secret, "thaiprompt-smschecker-v1:hmac-signing", 100000, 32)
```

**Android (Kotlin):**
```kotlin
private fun deriveKey(secret: String, context: String = "encryption"): SecretKey {
    val salt = "thaiprompt-smschecker-v1:$context".toByteArray(Charsets.UTF_8)
    val spec = PBEKeySpec(secret.toCharArray(), salt, 100_000, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val keyBytes = factory.generateSecret(spec).encoded
    spec.clearPassword()
    return SecretKeySpec(keyBytes, "AES")
}
```

**Laravel (PHP):**
```php
private function deriveKey(string $secret, string $context = 'encryption'): string
{
    $salt = "thaiprompt-smschecker-v1:{$context}";
    return hash_pbkdf2('sha256', $secret, $salt, 100000, 32, true);
}
```

> **Note**: Both sides use identical salt format and iteration count, ensuring keys match.
>
> **Breaking change**: PBKDF2 replaces the previous zero-padding scheme. Android and Laravel must be deployed simultaneously or encryption will fail.

---

## Request Signing (HMAC-SHA256)

### Purpose

HMAC signatures ensure:
1. **Integrity** - payload has not been tampered with
2. **Authentication** - only the device with the correct secret key can sign
3. **Non-repudiation** - server can verify the request came from a legitimate device

### Signature Computation

The HMAC uses a **dedicated key** derived from the secret (separate from the encryption key):

```
hmac_key = PBKDF2(secret, "thaiprompt-smschecker-v1:hmac-signing", 100000, 32)
signature_data = encrypted_payload + nonce + timestamp
signature = Base64( HMAC-SHA256(signature_data, hmac_key) )
```

Using a separate key for HMAC prevents potential key-reuse vulnerabilities between encryption and authentication.

### Verification

The server uses `hash_equals()` for constant-time comparison, preventing timing attacks.

```php
$hmacKey = $this->deriveKey($secretKey, 'hmac-signing');
$expected = base64_encode(hash_hmac('sha256', $data, $hmacKey, true));
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
- **Pessimistic locking** -- `generate()` wraps suffix selection in `DB::transaction` with `lockForUpdate()` to prevent two concurrent requests from receiving the same suffix
- **Cryptographic random** -- suffix selection uses `random_int()` (CSPRNG) instead of `array_rand()`
- **Locked matching** -- `findMatch()` uses `lockForUpdate()` to prevent double-matching of the same unique amount
- **Bank account verification** -- `attemptMatch()` verifies the SMS sender's bank account is registered before auto-confirming, preventing fraud from unrecognized accounts

### Double-Spend Prevention

The `PaymentService::completePayment()` method uses pessimistic locking to prevent race conditions:

```php
DB::transaction(function () use ($transaction) {
    $transaction = PaymentTransaction::lockForUpdate()->find($transaction->id);
    if ($transaction->isCompleted()) return true;  // Already done
    $transaction->markAsCompleted();
    // ... fire webhooks
});
```

This ensures that even if two SMS notifications arrive simultaneously for the same amount, only one payment completion occurs.

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
- [ ] Set strong, unique `SMSCHECKER_API_KEY` and `SMSCHECKER_SECRET_KEY` (min 16 chars each)
- [ ] Enable `APP_DEBUG=false` in Laravel
- [ ] Configure rate limiting
- [ ] Set up log monitoring for security warnings
- [ ] Schedule `smschecker:cleanup` to run every 5 minutes
- [ ] Review device list regularly (`smschecker:status`)
- [ ] Keep PHP, Laravel, and Android dependencies updated
- [ ] Deploy Android + Laravel simultaneously when updating key derivation (PBKDF2 must match)
- [ ] Register known bank accounts in the system for account verification

### Device Security

- [ ] Keep Android app updated
- [ ] Use device with hardware-backed keystore (TEE/Strongbox)
- [ ] Do not root/jailbreak the device
- [ ] Keep screen lock enabled
- [ ] Restrict SMS permissions to this app only
- [ ] Grant Notification Access permission for bank app push notifications

### Network Security

- [ ] Use HTTPS only (no HTTP fallback)
- [ ] Consider IP whitelisting for the API
- [ ] Monitor for unusual request patterns
- [ ] Set up alerts for signature verification failures
- [ ] Verify HTTP logging is disabled in production builds (auto-enforced since v1.1)

---

## Threat Model

| Threat | Mitigation |
|--------|------------|
| SMS interception | AES-256-GCM encryption of payload |
| Request tampering | HMAC-SHA256 signature verification (dedicated HMAC key) |
| Replay attacks | Nonce + Timestamp validation |
| Key extraction (APK decompile) | No hardcoded keys; keys derived at runtime via PBKDF2 |
| Key extraction (device) | EncryptedSharedPreferences + Android Keystore (TEE/Strongbox) |
| Credential theft (server) | `hidden` attributes on model, HTTPS |
| Brute force | Rate limiting, 64-char hex keys, PBKDF2 100K iterations |
| Timing attacks | `hash_equals()` and constant-time comparison |
| Amount collision | Unique decimal suffix with pessimistic locking |
| Double-spend | `lockForUpdate()` on payment completion |
| Fraudulent SMS | Bank account verification before auto-match |
| Race condition (amount) | `DB::transaction` + `lockForUpdate()` in generate/match |
| Man-in-the-middle | HTTPS/TLS 1.2+ (enforced in QR scanner) |
| Insecure QR code | HTTPS URL validation, minimum key length (16 chars) |
| Device compromise | Device status management (can block remotely) |
| HTTP log leakage | Logging disabled in production builds |
| Boot receiver abuse | `android:exported="false"` on BootReceiver |

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
