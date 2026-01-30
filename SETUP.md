# SMS Payment Checker - Quick Start Guide

## Overview

SMS Payment Checker consists of three parts:

1. **Android App** (`app/`) - Reads bank SMS, auto-detects transactions, matches with orders, TTS alerts
2. **Laravel Plugin** (`laravel-plugin/`) - Composer package with controllers, models, routes, QR setup
3. **Laravel API** (`laravel-api/`) - Standalone API integration files

---

## Part 1: Laravel Server Setup

### Step 1: Copy Plugin Files

Copy files from `laravel-plugin/src/` to your Laravel project:

```
laravel-plugin/src/Http/Controllers/Api/V1/SmsPaymentController.php  → app/Http/Controllers/Api/V1/
laravel-plugin/src/Http/Controllers/Web/QrConfigController.php       → app/Http/Controllers/Web/
laravel-plugin/src/Http/Middleware/VerifySmsCheckerDevice.php         → app/Http/Middleware/
laravel-plugin/src/Models/SmsCheckerDevice.php                       → app/Models/
laravel-plugin/src/Models/SmsPaymentNotification.php                 → app/Models/
laravel-plugin/src/Models/UniquePaymentAmount.php                    → app/Models/
laravel-plugin/src/Services/SmsPaymentService.php                    → app/Services/
laravel-plugin/src/Console/CleanupCommand.php                        → app/Console/Commands/
laravel-plugin/src/Console/CreateDeviceCommand.php                   → app/Console/Commands/
laravel-plugin/src/Console/StatusCommand.php                         → app/Console/Commands/
laravel-plugin/config/smschecker.php                                 → config/
laravel-plugin/database/migrations/*.php                             → database/migrations/
laravel-plugin/routes/sms_payment_api.php                            → routes/
laravel-plugin/routes/sms_payment_web.php                            → routes/
laravel-plugin/resources/views/smschecker/qr-setup.blade.php         → resources/views/smschecker/
```

### Step 2: Register Middleware

**Laravel 11** (`bootstrap/app.php`):
```php
->withMiddleware(function (Middleware $middleware) {
    $middleware->alias([
        'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
    ]);
})
```

**Laravel 10** (`app/Http/Kernel.php`):
```php
protected $middlewareAliases = [
    // ... existing
    'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
];
```

### Step 3: Register Routes

In `routes/api.php`:
```php
require __DIR__ . '/sms_payment_api.php';
```

In `routes/web.php`:
```php
require __DIR__ . '/sms_payment_web.php';
```

### Step 4: Run Migrations

```bash
php artisan migrate
```

Creates tables: `sms_checker_devices`, `sms_payment_notifications`, `unique_payment_amounts`, `sms_payment_nonces`, `order_approvals`

### Step 5: Create Device

```bash
php artisan smschecker:create-device "My Phone"
# or with user: php artisan smschecker:create-device "My Phone" --user=1
```

Save the **API Key** and **Secret Key** displayed.

### Step 6: Environment Variables (Optional)

```env
SMSCHECKER_TIMESTAMP_TOLERANCE=300
SMSCHECKER_AMOUNT_EXPIRY=30
SMSCHECKER_RATE_LIMIT=30
SMSCHECKER_AUTO_CONFIRM=true
SMSCHECKER_NOTIFY_ON_MATCH=true
SMSCHECKER_LOG_LEVEL=info
```

### Step 7: Schedule Cleanup

```php
// routes/console.php or scheduler
Schedule::command('smschecker:cleanup')->hourly();
```

### Step 8: Verify

```bash
php artisan smschecker:status
```

---

## Part 2: Android App Setup

### 1. Build

Open `app/` folder in Android Studio, then Build > Make Project (Ctrl+F9).

### 2. Install & Permissions

Install on device. Grant permissions:
- **SMS** - Read incoming bank SMS + scan inbox
- **Notifications** - Show transaction alerts
- **Camera** - Scan QR codes for server setup

---

## Part 3: Connect App to Server

### Method A: QR Code Scan (Recommended)

1. Login to your Laravel admin panel
2. Navigate to: `https://your-domain.com/smschecker/device/{device_id}/qr`
3. Open Android app > **Settings** > tap **"Scan QR"**
4. Point camera at QR code on screen
5. Server is auto-configured instantly

The QR code contains this JSON payload:
```json
{
  "type": "smschecker_config",
  "version": 1,
  "url": "https://your-domain.com",
  "apiKey": "your_64char_hex_api_key",
  "secretKey": "your_64char_hex_secret_key",
  "deviceName": "My Phone"
}
```

### Method B: Manual Entry

1. Open app > **Settings** tab
2. Tap **"Add Server"**
3. Enter: Server Name, URL, API Key, Secret Key
4. Check "Set as default"
5. Tap **Save**

---

## Part 4: Integration with Checkout

### Generate Unique Amount

```php
use App\Services\SmsPaymentService;

public function bankTransfer(Request $request, SmsPaymentService $smsService)
{
    $order = Order::find($request->order_id);

    $uniqueAmount = $smsService->generateUniqueAmount(
        baseAmount: $order->total,
        transactionId: $order->paymentTransaction->id,
        transactionType: 'order',
        expiryMinutes: 30
    );

    if (!$uniqueAmount) {
        return back()->with('error', 'Unable to generate payment amount');
    }

    return view('checkout.bank-transfer', [
        'order' => $order,
        'paymentAmount' => $uniqueAmount->unique_amount,
        'displayAmount' => number_format($uniqueAmount->unique_amount, 2),
        'expiresAt' => $uniqueAmount->expires_at,
    ]);
}
```

### Payment Flow

```
1. Customer places order (500.00 THB)
2. System generates unique amount (500.37 THB)
3. Customer transfers exactly 500.37 via bank app
4. Bank sends SMS to phone
5. Android app detects SMS -> parses amount + bank
6. Encrypts (AES-256-GCM) + signs (HMAC-SHA256)
7. Sends to Laravel API via HTTPS
8. API decrypts -> matches 500.37 with reserved amount
9. Payment auto-confirmed + TTS announcement
```

---

## Part 5: QR Code for Plugins

Any plugin (WooCommerce, Shopify, custom) can generate a QR code for device setup by creating the same JSON structure:

```json
{
  "type": "smschecker_config",
  "version": 1,
  "url": "https://your-domain.com",
  "apiKey": "64_hex_char_api_key",
  "secretKey": "64_hex_char_secret_key",
  "deviceName": "Device Name"
}
```

**Requirements:**
- `type` must be exactly `"smschecker_config"` (the Android app validates this)
- `version` must be `1`
- `url` must be the base URL of the server (no trailing slash)
- `apiKey` is 64 hex characters from `bin2hex(random_bytes(32))`
- `secretKey` is 64 hex characters from `bin2hex(random_bytes(32))`
- `deviceName` is optional (fallback to default)

**Laravel Web Routes:**
| Method | URL | Description |
|--------|-----|-------------|
| GET | `/smschecker/device/{device}/qr` | QR code setup page (requires auth) |
| GET | `/smschecker/device/{device}/qr.json` | JSON config endpoint (requires auth) |

The QR setup page (`qr-setup.blade.php`) uses `qrcodejs` CDN to generate the QR code client-side.

---

## API Endpoints

### Device Auth (X-Api-Key + X-Device-Id)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/sms-payment/notify` | Send encrypted SMS notification |
| GET | `/api/v1/sms-payment/status` | Check device status |
| POST | `/api/v1/sms-payment/register-device` | Register/update device |
| GET | `/api/v1/sms-payment/orders` | Get order list |
| POST | `/api/v1/sms-payment/orders/{id}/approve` | Approve order |
| POST | `/api/v1/sms-payment/orders/{id}/reject` | Reject order |
| POST | `/api/v1/sms-payment/orders/bulk-approve` | Bulk approve |
| GET | `/api/v1/sms-payment/orders/sync` | Sync orders |
| GET | `/api/v1/sms-payment/device-settings` | Get device settings |
| PUT | `/api/v1/sms-payment/device-settings` | Update settings |
| GET | `/api/v1/sms-payment/dashboard-stats` | Dashboard statistics |

### Admin Auth (Bearer Token / Sanctum)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/sms-payment/generate-amount` | Generate unique amount |
| GET | `/api/v1/sms-payment/notifications` | Notification history |

---

## Supported Banks

| Code | Bank | SMS Senders |
|------|------|-------------|
| KBANK | Kasikorn Bank | KBANK, KBank, K-Bank, KPlus |
| SCB | Siam Commercial Bank | SCB, SCBeasy, SCBEasy |
| KTB | Krungthai Bank | KTB, Krungthai, KTB-BANK |
| BBL | Bangkok Bank | BBL, Bangkok Bank, BualuangiBanking |
| GSB | Government Savings Bank | GSB, MyMo, MYMO |
| BAY | Bank of Ayudhya | BAY, KMA, Krungsri |
| TTB | TMBThanachart Bank | TTB, ttb, TMB, Thanachart, ttbbank |
| PROMPTPAY | PromptPay | PromptPay, PROMPTPAY |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| App not receiving SMS | Check SMS permission, check battery optimization settings |
| QR code not scanning | Ensure camera permission granted, hold steady, good lighting |
| Sync failing | Verify URL starts with `https://`, check API key matches |
| Unique amount not matching | Customer must transfer exact amount, check expiry (default 30 min) |
| TTS not working | Check TTS toggle in Settings, verify Thai TTS data installed on device |
| SMS not auto-detected | App uses heuristic detection; add custom sender rules in SMS Matcher |

---

## Further Documentation

- [API Reference](docs/API.md) - Complete endpoint documentation
- [Banks & SMS Patterns](docs/BANKS.md) - Bank-specific parsing details
- [Security Architecture](docs/SECURITY.md) - Encryption, signing, threat model
- [Detailed Setup](docs/SETUP.md) - In-depth installation guide
