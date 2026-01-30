# SMS Payment Checker

<!-- Badges -->
![Platform](https://img.shields.io/badge/platform-Android-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue?logo=kotlin)
![Laravel](https://img.shields.io/badge/Laravel-10%20%7C%2011-red?logo=laravel)
![PHP](https://img.shields.io/badge/PHP-8.1%2B-777BB4?logo=php)
![License](https://img.shields.io/badge/license-MIT-brightgreen)
![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)
![Target SDK](https://img.shields.io/badge/targetSdk-34-blue)

Real-time SMS payment verification system for Thai banks. An Android app intercepts bank SMS notifications, encrypts them with AES-256-GCM, and forwards them to a Laravel backend that auto-matches transactions to pending orders using unique decimal amounts.

Supports **8 Thai banks**: KBANK, SCB, KTB, BBL, GSB, BAY, TTB, and PromptPay.

---

## Architecture

```
+------------------+       SMS        +---------------------+
|   Thai Bank      | ───────────────> |   Android Device    |
|   (KBANK, SCB,   |                  |                     |
|    KTB, BBL,     |                  |  +--------------+   |
|    GSB, BAY,     |                  |  | SMS Receiver |   |
|    TTB, Prompt)  |                  |  +------+-------+   |
+------------------+                  |         |           |
                                      |         v           |
                                      |  +--------------+   |
                                      |  | Bank Parser  |   |
                                      |  | (8 banks +   |   |
                                      |  |  heuristic)  |   |
                                      |  +------+-------+   |
                                      |         |           |
                                      |         v           |
                                      |  +--------------+   |
                                      |  | AES-256-GCM  |   |
+------------------+                  |  | Encrypt +    |   |
|   Laravel API    | <── HTTPS ────── |  | HMAC Sign    |   |
|                  |                  |  +--------------+   |
|  +-----------+   |                  |         |           |
|  | Decrypt & |   |                  |         v           |
|  | Verify    |   |                  |  +--------------+   |
|  +-----+-----+   |                  |  | TTS Voice    |   |
|        |         |                  |  | Alert        |   |
|        v         |                  |  +--------------+   |
|  +-----------+   |                  +---------------------+
|  | Amount    |   |
|  | Matcher   |   |        QR Scan
|  +-----+-----+   |  <─────────────  Device Setup
|        |         |
|        v         |
|  +-----------+   |
|  | Order     |   |
|  | Approval  |   |
|  +-----------+   |
+------------------+
```

## Payment Flow

```
1. Customer places order         ──>  500.00 THB
2. System generates unique amount ──>  500.37 THB  (unique decimal)
3. Customer transfers exact amount to bank
4. Bank sends SMS to registered phone
5. Android app intercepts SMS ──> parses bank + amount
6. App encrypts payload (AES-256-GCM) + signs (HMAC-SHA256)
7. App sends encrypted data to Laravel API over HTTPS
8. API decrypts, matches 500.37 to pending order
9. Order auto-confirmed, webhook/callback fires
10. TTS voice announces: "Received 500.37 baht from KBANK"
```

## Features

- **Real-time SMS interception** from 8 Thai banks with intelligent heuristic detection for unknown senders
- **AES-256-GCM encryption** with HMAC-SHA256 request signing and nonce-based replay protection
- **QR code device setup** -- Laravel generates a QR code, the Android app scans it to auto-configure server URL, API key, and secret key
- **Unique decimal amount matching** -- transforms round amounts (500.00) into unique decimals (500.37) so each transaction can be matched unambiguously
- **Order approval system** with auto, manual, and smart approval modes, plus bulk approve
- **Dashboard with statistics** -- transaction counts, success rates, bank breakdowns
- **Multi-server support** -- one Android device can report to multiple Laravel servers
- **Dark/Light theme toggle** with Material 3 dynamic theming
- **Thai/English language support** (full i18n)
- **TTS voice alerts** using Android TextToSpeech with Thai locale
- **SMS auto-scan** with background service and boot receiver for 24/7 operation

## Project Structure

```
SmsChecker/
|-- app/                          # Android application
|   |-- src/main/java/com/thaiprompt/smschecker/
|   |   |-- data/
|   |   |   |-- api/              # Retrofit API client & services
|   |   |   |-- db/               # Room database, DAOs, converters
|   |   |   |-- model/            # Data classes (BankTransaction, OrderApproval, etc.)
|   |   |   |-- repository/       # Transaction & Order repositories
|   |   |-- receiver/             # SmsBroadcastReceiver, BootReceiver
|   |   |-- security/             # CryptoManager (AES-256-GCM, HMAC-SHA256)
|   |   |-- service/              # SmsProcessingService, OrderSyncWorker
|   |   |-- ui/                   # Jetpack Compose screens
|   |   |   |-- components/       # Charts, DateRangePicker
|   |   |   |-- dashboard/        # Dashboard screen + ViewModel
|   |   |   |-- orders/           # Orders screen + ViewModel
|   |   |   |-- transactions/     # Transaction list + ViewModel
|   |   |-- SmsCheckerApp.kt      # Hilt Application class
|   |-- src/main/res/             # Resources, themes, strings (EN/TH)
|
|-- laravel-api/                  # Standalone Laravel integration files
|   |-- app/
|   |   |-- Http/
|   |   |   |-- Controllers/Api/V1/SmsPaymentController.php
|   |   |   |-- Middleware/VerifySmsCheckerDevice.php
|   |   |-- Models/               # SmsCheckerDevice, SmsPaymentNotification, etc.
|   |   |-- Services/             # SmsPaymentService
|   |-- config/smschecker.php     # Configuration file
|   |-- database/migrations/      # DB migrations
|   |-- routes/sms_payment_api.php
|
|-- laravel-plugin/               # Composer package (thaiprompt/smschecker-laravel)
|   |-- src/                      # Full plugin source (controllers, models, services)
|   |-- config/                   # Publishable config
|   |-- database/                 # Migrations & seeders
|   |-- resources/views/          # QR setup Blade template
|   |-- routes/                   # API & web routes
|   |-- tests/                    # PHPUnit tests
|   |-- composer.json
|
|-- docs/                         # Documentation
|   |-- API.md                    # Full API reference
|   |-- BANKS.md                  # Supported banks & SMS formats
|   |-- SECURITY.md               # Encryption & signing details
|   |-- SETUP.md                  # Step-by-step setup guide
```

## Tech Stack

### Android

| Component       | Version / Library              |
|-----------------|-------------------------------|
| Language        | Kotlin 1.9.22                 |
| UI              | Jetpack Compose BOM 2024.01.00 |
| DI              | Hilt 2.50                     |
| Database        | Room 2.6.1                    |
| Networking      | Retrofit 2.9.0                |
| Camera          | CameraX 1.3.1                |
| QR Scanning     | ML Kit Barcode 17.2.0        |
| TTS             | Android TextToSpeech (built-in) |
| Min SDK         | 26 (Android 8.0)             |
| Target SDK      | 34 (Android 14)              |
| Build           | Gradle 8.5, AGP 8.2.2       |

### Laravel

| Component       | Version                       |
|-----------------|-------------------------------|
| PHP             | 8.1+                         |
| Framework       | Laravel 10 or 11             |
| Auth            | Laravel Sanctum              |
| Testing         | PHPUnit 10 / 11              |
| Package         | `thaiprompt/smschecker-laravel` |

## Getting Started

### 1. Laravel Backend Setup

**Option A: Composer Plugin (recommended)**

```bash
composer require thaiprompt/smschecker-laravel
php artisan vendor:publish --tag=smschecker-config
php artisan migrate
```

**Option B: Standalone Integration**

Copy the files from `laravel-api/` into your Laravel project:

```bash
# Copy models, controllers, middleware, service, config, and migrations
# Then add the routes to your routes/api.php:
require __DIR__ . '/sms_payment_api.php';
```

**Configure** your `.env`:

```env
SMSCHECKER_TIMESTAMP_TOLERANCE=300
SMSCHECKER_AMOUNT_EXPIRY=30
SMSCHECKER_RATE_LIMIT=30
SMSCHECKER_AUTO_CONFIRM=true
SMSCHECKER_APPROVAL_MODE=auto
SMSCHECKER_LOG_LEVEL=info
```

**Create a device** and generate its QR code:

```bash
php artisan smschecker:create-device --name="Shop Phone"
```

Then visit `/smschecker/device/{id}/qr` in your browser to display the QR code.

### 2. Android App Setup

1. Clone the repo and open in Android Studio.
2. Build and install the app on your device.
3. Grant SMS permission when prompted.
4. Scan the QR code from the Laravel admin panel.

The QR code contains all connection details:

```json
{
  "type": "smschecker_config",
  "version": 1,
  "url": "https://your-domain.com",
  "apiKey": "device-api-key",
  "secretKey": "device-secret-key",
  "deviceName": "Shop Phone"
}
```

After scanning, the app auto-configures the server URL, API key, and encryption secret. SMS monitoring starts immediately.

### 3. Integrate with Your Checkout

```php
use App\Services\SmsPaymentService;

// During checkout -- generate a unique amount
$service = app(SmsPaymentService::class);
$uniqueAmount = $service->generateUniqueAmount(500.00);
// Returns e.g. 500.37 -- display this to the customer

// Register callbacks in config/smschecker.php
'on_order_approved' => function ($transactionId, $approval) {
    // Mark order as paid in your system
},
```

## API Endpoints

All endpoints are prefixed with `/api/v1/sms-payment/`.

### Device-Authenticated Endpoints (Header: `X-Api-Key`)

| Method | Path                        | Description                      |
|--------|-----------------------------|----------------------------------|
| POST   | `/notify`                   | Submit encrypted SMS notification |
| GET    | `/status`                   | Check device connection status   |
| POST   | `/register-device`          | Register or update device info   |
| GET    | `/orders`                   | List orders for this device      |
| POST   | `/orders/{id}/approve`      | Approve a pending order          |
| POST   | `/orders/{id}/reject`       | Reject a pending order           |
| POST   | `/orders/bulk-approve`      | Bulk approve multiple orders     |
| GET    | `/orders/sync`              | Sync order state changes         |
| GET    | `/device-settings`          | Get device settings              |
| PUT    | `/device-settings`          | Update device settings           |
| GET    | `/dashboard-stats`          | Get dashboard statistics         |

### Admin-Authenticated Endpoints (Laravel Sanctum)

| Method | Path                        | Description                      |
|--------|-----------------------------|----------------------------------|
| POST   | `/generate-amount`          | Generate unique payment amount   |
| GET    | `/notifications`            | View notification history        |

### Web Routes (Session Auth)

| Method | Path                            | Description                    |
|--------|---------------------------------|--------------------------------|
| GET    | `/smschecker/device/{id}/qr`    | Display device QR code page    |
| GET    | `/smschecker/device/{id}/qr.json` | QR config as JSON            |

## Security

Communication between the Android app and Laravel API is secured with multiple layers:

- **AES-256-GCM** -- All SMS payloads are encrypted with a 256-bit key. GCM mode provides both confidentiality and authentication (96-bit IV, 128-bit auth tag).
- **HMAC-SHA256** -- Every request includes a signature computed over the payload, timestamp, and nonce. The server verifies this before processing.
- **Nonce + Timestamp** -- Each request carries a unique nonce and timestamp. The server rejects replayed or expired requests (default tolerance: 300 seconds).
- **Constant-time comparison** -- HMAC verification uses constant-time string comparison to prevent timing attacks.
- **HTTPS only** -- The app enforces TLS for all API communication.
- **Per-device keys** -- Each device has its own API key and secret key, generated server-side.

See [docs/SECURITY.md](docs/SECURITY.md) for the full specification.

## Supported Banks

| Code       | Bank Name                  |
|------------|---------------------------|
| KBANK      | Kasikorn Bank             |
| SCB        | Siam Commercial Bank      |
| KTB        | Krungthai Bank            |
| BBL        | Bangkok Bank              |
| GSB        | Government Savings Bank   |
| BAY        | Bank of Ayudhya (Krungsri)|
| TTB        | TMBThanachart Bank        |
| PROMPTPAY  | PromptPay                 |

The app also includes **heuristic detection** for unknown SMS senders, parsing amount and transfer keywords from message body even when the sender is not in the known bank list.

See [docs/BANKS.md](docs/BANKS.md) for SMS format details per bank.

## Configuration Reference

Key settings in `config/smschecker.php`:

| Setting                    | Default | Description                                      |
|----------------------------|---------|--------------------------------------------------|
| `timestamp_tolerance`      | 300     | Max request age in seconds                       |
| `unique_amount_expiry`     | 30      | Unique amount TTL in minutes                     |
| `max_pending_per_amount`   | 99      | Max concurrent unique amounts per base amount    |
| `rate_limit_per_minute`    | 30      | Max notifications per device per minute          |
| `nonce_expiry_hours`       | 24      | Nonce cleanup threshold in hours                 |
| `auto_confirm_matched`     | true    | Auto-confirm when amount matches                 |
| `default_approval_mode`    | auto    | Approval mode: `auto`, `manual`, or `smart`      |

## Documentation

- [API Reference](docs/API.md) -- Full endpoint documentation with request/response examples
- [Bank SMS Formats](docs/BANKS.md) -- SMS patterns for each supported Thai bank
- [Security Details](docs/SECURITY.md) -- Encryption, signing, and threat model
- [Setup Guide](docs/SETUP.md) -- Step-by-step installation and configuration

## License

This project is licensed under the MIT License.
