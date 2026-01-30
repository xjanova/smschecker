# SMS Payment Checker - Setup Guide

## Overview

SMS Payment Checker consists of two parts:
1. **Android App** - Reads bank SMS and sends to server
2. **Laravel API** - Receives notifications and matches payments

---

## Part 1: Laravel API Setup (Server)

### 1. Copy Files to Thaiprompt-Affiliate

Copy these files to your Laravel project:

```
laravel-api/app/Models/SmsCheckerDevice.php       → app/Models/
laravel-api/app/Models/SmsPaymentNotification.php  → app/Models/
laravel-api/app/Models/UniquePaymentAmount.php     → app/Models/
laravel-api/app/Services/SmsPaymentService.php     → app/Services/
laravel-api/app/Http/Controllers/Api/V1/SmsPaymentController.php → app/Http/Controllers/Api/V1/
laravel-api/app/Http/Middleware/VerifySmsCheckerDevice.php → app/Http/Middleware/
laravel-api/database/migrations/                   → database/migrations/
laravel-api/config/smschecker.php                  → config/
```

### 2. Register Middleware

In `app/Http/Kernel.php` add:

```php
protected $middlewareAliases = [
    // ... existing middlewares
    'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
];
```

Or for Laravel 11 in `bootstrap/app.php`:

```php
->withMiddleware(function (Middleware $middleware) {
    $middleware->alias([
        'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
    ]);
})
```

### 3. Add Routes

In `routes/api.php` add:

```php
require __DIR__ . '/sms_payment_api.php';
```

Or copy the route file content directly into `api.php`.

### 4. Run Migration

```bash
php artisan migrate
```

### 5. Create a Device (via tinker)

```bash
php artisan tinker
```

```php
use App\Models\SmsCheckerDevice;

$device = SmsCheckerDevice::create([
    'device_id' => 'SMSCHK-YOUR-ID',
    'device_name' => 'My Phone',
    'api_key' => SmsCheckerDevice::generateApiKey(),
    'secret_key' => SmsCheckerDevice::generateSecretKey(),
    'status' => 'active',
]);

echo "API Key: " . $device->api_key;
echo "Secret Key: " . $device->secret_key;
// Save these! You'll need them for the Android app.
```

### 6. Add .env Variables (Optional)

```env
SMSCHECKER_TIMESTAMP_TOLERANCE=300
SMSCHECKER_AMOUNT_EXPIRY=30
SMSCHECKER_RATE_LIMIT=30
SMSCHECKER_AUTO_CONFIRM=true
SMSCHECKER_NOTIFY_ON_MATCH=true
SMSCHECKER_LOG_LEVEL=info
```

---

## Part 2: Android App Setup

### 1. Open in Android Studio

Open the `SmsChecker` folder in Android Studio.

### 2. Build the Project

```
Build → Make Project (Ctrl+F9)
```

### 3. Configure Server Connection

In the app:
1. Go to **Settings** tab
2. Tap **Add Server**
3. Enter:
   - **Server Name**: Your server name
   - **Server URL**: `https://your-domain.com`
   - **API Key**: (from step 5 above)
   - **Secret Key**: (from step 5 above)
4. Check "Set as default"
5. Tap **Save**

### 4. Grant Permissions

The app will request:
- **SMS** - To read bank transaction SMS
- **Notifications** - To show transaction alerts

---

## How It Works

### Flow:
1. Bank sends SMS notification
2. Android app receives SMS via BroadcastReceiver
3. BankSmsParser identifies if it's a bank transaction
4. Extracts: bank, amount, type (credit/debit), account, reference
5. Encrypts data with AES-256-GCM
6. Signs with HMAC-SHA256
7. Sends to Laravel API via HTTPS
8. Laravel decrypts, validates, and records notification
9. Auto-matches credit amounts with pending transactions

### Unique Decimal Amounts:
When a customer needs to pay 500 THB:
- System generates unique amount: 500.37 THB
- Customer transfers exactly 500.37 THB
- SMS is received and parsed
- Amount 500.37 matches the reserved unique amount
- Payment is auto-confirmed

### Security:
- TLS/HTTPS for transport
- AES-256-GCM payload encryption
- HMAC-SHA256 request signing
- Nonce-based replay attack prevention
- Request timestamp validation (5-minute window)
- Device ID + API Key authentication
- Android Keystore for secure key storage

---

## Integrating Unique Amount in Checkout

In your checkout process, generate a unique amount:

```php
// In your CheckoutController
use App\Services\SmsPaymentService;

public function processPayment(Request $request, SmsPaymentService $smsService)
{
    $order = Order::find($request->order_id);

    // Generate unique amount for bank transfer
    $uniqueAmount = $smsService->generateUniqueAmount(
        baseAmount: $order->total,
        transactionId: $order->paymentTransaction->id,
        transactionType: 'order',
        expiryMinutes: 30
    );

    if (!$uniqueAmount) {
        return back()->with('error', 'Unable to generate payment amount. Please try again.');
    }

    return view('checkout.bank-transfer', [
        'order' => $order,
        'paymentAmount' => $uniqueAmount->unique_amount,
        'expiresAt' => $uniqueAmount->expires_at,
    ]);
}
```

---

## Supported Banks

| Bank | Code | SMS Patterns |
|------|------|-------------|
| Kasikorn | KBANK | รับโอน, เงินเข้า |
| SCB | SCB | โอนเข้า, Received |
| Krungthai | KTB | เงินเข้า |
| Bangkok Bank | BBL | Transfer In |
| GSB | GSB | เงินเข้า |
| Krungsri | BAY | รับโอน |
| TTB | TTB | โอนเข้า |
| PromptPay | PROMPTPAY | PromptPay |

---

## API Endpoints

### App → Server (Device Auth)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/sms-payment/notify` | Send SMS notification |
| GET | `/api/v1/sms-payment/status` | Check device status |
| POST | `/api/v1/sms-payment/register-device` | Register device |

### Web/Admin (User Auth)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/sms-payment/generate-amount` | Generate unique amount |
| GET | `/api/v1/sms-payment/notifications` | View notification history |
