# SMS Payment Checker - Setup Guide

Complete guide covering Laravel plugin installation, Android app setup, QR code device pairing, checkout integration, and troubleshooting.

## Prerequisites

| Component | Requirement |
|-----------|------------|
| **Android App** | Android Studio 2023.2+, JDK 17+, Android device or emulator (API 26+ / Android 8.0+) |
| **Laravel Plugin** | PHP 8.1+, Laravel 10 or 11, MySQL or PostgreSQL, Composer 2.x |

---

## Part 1: Laravel Plugin Installation

### Method A: Copy Files (Standalone)

Copy files from `laravel-plugin/src/` into your existing Laravel project. The directory mapping is:

| Source (this repo) | Destination (your Laravel project) |
|---|---|
| `src/Http/Controllers/Api/V1/SmsPaymentController.php` | `app/Http/Controllers/Api/V1/` |
| `src/Http/Controllers/Web/QrConfigController.php` | `app/Http/Controllers/Web/` |
| `src/Http/Middleware/VerifySmsCheckerDevice.php` | `app/Http/Middleware/` |
| `src/Models/SmsCheckerDevice.php` | `app/Models/` |
| `src/Models/SmsPaymentNotification.php` | `app/Models/` |
| `src/Models/UniquePaymentAmount.php` | `app/Models/` |
| `src/Services/SmsPaymentService.php` | `app/Services/` |
| `src/Console/CleanupCommand.php` | `app/Console/Commands/` |
| `src/Console/CreateDeviceCommand.php` | `app/Console/Commands/` |
| `src/Console/StatusCommand.php` | `app/Console/Commands/` |
| `config/smschecker.php` | `config/` |
| `database/migrations/2024_01_01_000001_create_sms_payment_tables.php` | `database/migrations/` |
| `routes/sms_payment_api.php` | `routes/` |
| `routes/sms_payment_web.php` | `routes/` |
| `resources/views/smschecker/qr-setup.blade.php` | `resources/views/smschecker/` |

**Copy commands (Linux/macOS):**

```bash
# From the repository root directory

# Controllers
cp laravel-plugin/src/Http/Controllers/Api/V1/SmsPaymentController.php \
   your-laravel/app/Http/Controllers/Api/V1/

mkdir -p your-laravel/app/Http/Controllers/Web
cp laravel-plugin/src/Http/Controllers/Web/QrConfigController.php \
   your-laravel/app/Http/Controllers/Web/

# Middleware
cp laravel-plugin/src/Http/Middleware/VerifySmsCheckerDevice.php \
   your-laravel/app/Http/Middleware/

# Models
cp laravel-plugin/src/Models/SmsCheckerDevice.php \
   laravel-plugin/src/Models/SmsPaymentNotification.php \
   laravel-plugin/src/Models/UniquePaymentAmount.php \
   your-laravel/app/Models/

# Services
mkdir -p your-laravel/app/Services
cp laravel-plugin/src/Services/SmsPaymentService.php \
   your-laravel/app/Services/

# Console Commands
cp laravel-plugin/src/Console/CleanupCommand.php \
   laravel-plugin/src/Console/CreateDeviceCommand.php \
   laravel-plugin/src/Console/StatusCommand.php \
   your-laravel/app/Console/Commands/

# Config
cp laravel-plugin/config/smschecker.php your-laravel/config/

# Migrations
cp laravel-plugin/database/migrations/*.php your-laravel/database/migrations/

# Routes
cp laravel-plugin/routes/sms_payment_api.php your-laravel/routes/
cp laravel-plugin/routes/sms_payment_web.php your-laravel/routes/

# Views
mkdir -p your-laravel/resources/views/smschecker
cp laravel-plugin/resources/views/smschecker/qr-setup.blade.php \
   your-laravel/resources/views/smschecker/
```

### Step 1: Register Middleware

**Laravel 11** -- Add the alias in `bootstrap/app.php`:

```php
->withMiddleware(function (Middleware $middleware) {
    $middleware->alias([
        // ... existing aliases
        'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
    ]);
})
```

**Laravel 10** -- Add the alias in `app/Http/Kernel.php`:

```php
protected $middlewareAliases = [
    // ... existing aliases
    'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
];
```

The middleware authenticates incoming requests from the Android app by verifying the `X-Api-Key` header against registered devices in the database.

### Step 2: Register Routes

**API routes** -- Add to `routes/api.php`:

```php
// SMS Payment Checker API Routes
require __DIR__ . '/sms_payment_api.php';
```

This registers the following API endpoints (all under `/api/v1/sms-payment/`):

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/notify` | Device API key | Receive SMS notifications from Android app |
| `GET` | `/status` | Device API key | Check device status and pending count |
| `POST` | `/register-device` | Device API key | Register or update device info |
| `POST` | `/generate-amount` | Sanctum (web) | Generate unique payment amount for checkout |
| `GET` | `/notifications` | Sanctum (web) | View notification history (admin) |

**Web routes** -- Add to `routes/web.php`:

```php
// SMS Payment Checker Web Routes (QR code setup)
require __DIR__ . '/sms_payment_web.php';
```

This registers the QR code setup pages (protected by Laravel `auth` middleware):

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/smschecker/device/{device}/qr` | QR code setup page (HTML) |
| `GET` | `/smschecker/device/{device}/qr.json` | QR code data (JSON) |

### Step 3: Run Migrations

```bash
php artisan migrate
```

This creates 4 database tables:

| Table | Purpose |
|-------|---------|
| `sms_checker_devices` | Device management (API keys, status, last active) |
| `sms_payment_notifications` | Incoming SMS notification records |
| `unique_payment_amounts` | Decimal amount reservations for payment matching |
| `sms_payment_nonces` | Replay attack prevention (one-time-use request tokens) |

### Step 4: Create a Device

Run the Artisan command to create a device entry with API keys:

```bash
php artisan smschecker:create-device "My Phone"
```

To associate the device with a specific user:

```bash
php artisan smschecker:create-device "My Phone" --user=1
```

**Output example:**

```
============================================
  SMS Checker Device Created Successfully
============================================

+-------------+------------------------------------------+
| Field       | Value                                    |
+-------------+------------------------------------------+
| Device ID   | SMSCHK-A1B2C3D4                          |
| Device Name | My Phone                                 |
| Status      | active                                   |
| API Key     | sk_live_abc123...                         |
| Secret Key  | sec_abc123...                             |
+-------------+------------------------------------------+

  SAVE THESE KEYS! They cannot be retrieved later.
  Enter these in the Android app Settings -> Add Server

  Or scan the QR code for easy setup:
  https://your-domain.com/smschecker/device/1/qr
```

Save the **API Key** and **Secret Key** -- you will need them for the Android app. The command also prints a URL for the QR code setup page (see Part 3).

### Step 5: Environment Variables (Optional)

Add to your `.env` file to customize behavior:

```env
SMSCHECKER_TIMESTAMP_TOLERANCE=300      # Max request age in seconds (default: 300 = 5 min)
SMSCHECKER_AMOUNT_EXPIRY=30             # Unique amount reservation lifetime in minutes (default: 30)
SMSCHECKER_MAX_PENDING=99               # Max pending unique amounts per base amount (default: 99)
SMSCHECKER_RATE_LIMIT=30                # Max notifications per device per minute (default: 30)
SMSCHECKER_NONCE_EXPIRY=24              # Nonce expiry in hours (default: 24)
SMSCHECKER_AUTO_CONFIRM=true            # Auto-confirm matched payments (default: true)
SMSCHECKER_NOTIFY_ON_MATCH=true         # Send notification when payment matches (default: true)
SMSCHECKER_LOG_LEVEL=info               # Log verbosity: debug, info, warning (default: info)
```

### Step 6: Schedule Cleanup

The cleanup command removes expired unique amounts, old nonces (older than 24 hours), and stale pending notifications (older than 7 days).

**Laravel 11** -- Add to `routes/console.php`:

```php
use Illuminate\Support\Facades\Schedule;

Schedule::command('smschecker:cleanup')->hourly();
```

**Laravel 10** -- Add to `app/Console/Kernel.php`:

```php
protected function schedule(Schedule $schedule): void
{
    $schedule->command('smschecker:cleanup')->hourly();
}
```

### Step 7: Verify Installation

```bash
php artisan smschecker:status
```

This displays a system status dashboard including device counts, notification statistics, active unique amount reservations, and stored nonce counts.

---

## Part 2: Android App Setup

### Step 1: Open the Project

1. Open **Android Studio** (2023.2 or later recommended)
2. Select **File > Open**
3. Navigate to the `app/` directory in the repository root
4. Wait for Gradle sync to complete (this downloads all dependencies including CameraX, ML Kit, Room, Hilt, and Compose libraries)

**Note:** The project requires JDK 17. The app targets Android API 34 with a minimum SDK of 26 (Android 8.0).

### Step 2: Build the App

```
Build > Make Project  (Ctrl+F9 / Cmd+F9)
```

Or build from the command line:

```bash
./gradlew assembleDebug
```

### Step 3: Install on Your Device

1. Connect your Android device via USB
2. Enable **Developer Options** and **USB Debugging** on the device
3. Select your device from the target dropdown in Android Studio
4. Click **Run > Run 'app'** (Shift+F10)

Alternatively, install a built APK manually:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Grant Permissions

When the app launches for the first time, it will request the following permissions:

| Permission | Purpose |
|------------|---------|
| **SMS** (Receive SMS, Read SMS) | Detect incoming bank transaction SMS messages |
| **Notifications** (Post Notifications) | Show transaction alerts and sync status |
| **Camera** | Scan QR codes for server configuration |

All three permissions are required for full functionality. On some Android skins (MIUI, ColorOS, One UI), you may also need to:

- Exempt the app from **battery optimization** (Settings > Battery > App battery management)
- Allow the app to **auto-start** on boot
- Grant **background activity** permissions

---

## Part 3: Connect the App to Your Server

### Method A: QR Code Setup (Recommended)

The QR code method is the fastest and least error-prone way to connect the Android app to your Laravel server. It encodes the server URL, API key, and secret key into a single scannable code.

**Steps:**

1. Log in to your Laravel admin panel in a web browser
2. Navigate to `/smschecker/device/{device_id}/qr` (the URL is printed when you create a device, or construct it using the device's database ID)
3. A page will display the QR code for that device, along with setup instructions
4. Open the **SMS Checker** Android app
5. Go to the **Settings** tab
6. Tap the **"Scan QR"** button (camera icon)
7. Point your phone's camera at the QR code displayed on screen
8. The server configuration is applied automatically

The QR code contains the following JSON payload:

```json
{
  "type": "smschecker_config",
  "version": 1,
  "url": "https://your-domain.com",
  "apiKey": "sk_live_...",
  "secretKey": "sec_...",
  "deviceName": "My Phone"
}
```

**Security note:** The QR code contains sensitive API credentials. The setup page (`qr-setup.blade.php`) is protected by Laravel's `auth` middleware, so only logged-in administrators can view it. Do not screenshot or share the QR code.

### Method B: Manual Entry

If QR scanning is not available (no camera, remote setup, etc.), you can enter the server details manually:

1. Open the **SMS Checker** app on your Android device
2. Go to the **Settings** tab
3. Tap **"Add Server"**
4. Fill in the following fields:
   - **Server Name**: A display name (e.g., "Production Server")
   - **Server URL**: Your Laravel server URL (must start with `https://`)
   - **API Key**: The API key from the `smschecker:create-device` command output
   - **Secret Key**: The secret key from the same command output
5. Check **"Set as default server"** if this is your primary server
6. Tap **Save**

### Verify the Connection

After configuring the server (via either method):

1. Toggle **SMS Monitoring** to Active in the app
2. On the server, run `php artisan smschecker:status` to confirm the device appears as active
3. Send a test SMS to the phone (or ask your bank to send a test notification) to verify end-to-end flow

---

## Part 4: Integration with Checkout

### Generate a Unique Amount During Checkout

When a customer selects bank transfer as their payment method, generate a unique amount so the system can automatically match the incoming SMS to the correct order.

```php
use App\Services\SmsPaymentService;

class CheckoutController extends Controller
{
    public function bankTransfer(Request $request, SmsPaymentService $smsService)
    {
        $order = Order::find($request->order_id);

        // Generate a unique amount based on the order total.
        // For example, an order of 500.00 might become 500.37.
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
            'paymentAmount' => $uniqueAmount->unique_amount,    // e.g., 500.37
            'displayAmount' => number_format($uniqueAmount->unique_amount, 2),
            'expiresAt' => $uniqueAmount->expires_at,
        ]);
    }
}
```

### Example Blade View (Show Payment Amount to Customer)

```blade
<div class="payment-instructions">
    <h2>Transfer Exactly This Amount</h2>
    <p class="amount">{{ $displayAmount }} THB</p>
    <p class="warning">
        You must transfer the exact amount including satang (decimals).
        The amount expires at {{ $expiresAt->format('H:i') }}.
    </p>

    <h3>Bank Account Details</h3>
    <p>Bank: Kasikorn Bank (KBANK)</p>
    <p>Account: 123-4-56789-0</p>
    <p>Name: Your Company Co., Ltd.</p>
</div>
```

### Payment Flow

The end-to-end flow from checkout to automatic order confirmation:

```
Step 1:  Customer places an order (e.g., 500.00 THB)
           |
Step 2:  System generates a unique amount (e.g., 500.37 THB)
           |
Step 3:  Customer sees 500.37 THB on the checkout page
           |
Step 4:  Customer opens their bank app and transfers exactly 500.37 THB
           |
Step 5:  Bank sends an SMS confirmation to the customer's phone
           |
Step 6:  SMS Checker Android app detects the SMS, parses the amount and bank
           |
Step 7:  App encrypts the data (AES-256-GCM) and sends it to the Laravel API
         with HMAC signature, nonce, and timestamp headers
           |
Step 8:  Laravel API decrypts the payload, verifies the signature,
         and matches 500.37 THB to the pending unique amount
           |
Step 9:  Payment is auto-confirmed. Order status updated to "paid".
```

### API Endpoint for Generating Amounts (Alternative)

You can also generate unique amounts via the REST API instead of using the service class directly:

```
POST /api/v1/sms-payment/generate-amount

Headers:
  Authorization: Bearer {sanctum_token}

Body (JSON):
  {
    "base_amount": 500.00,
    "transaction_id": 123,
    "transaction_type": "order",
    "expiry_minutes": 30
  }

Response:
  {
    "success": true,
    "message": "Unique amount generated",
    "data": {
      "base_amount": "500.00",
      "unique_amount": "500.37",
      "expires_at": "2024-01-15T14:30:00+07:00",
      "display_amount": "500.37"
    }
  }
```

---

## Part 5: QR Code Generation for Plugins (WooCommerce, etc.)

External plugins (WooCommerce, Shopify adapters, custom e-commerce platforms) can integrate with SMS Checker by using the same QR code JSON format for device provisioning.

### QR Code Data Format

Any system generating QR codes for SMS Checker device configuration must produce a JSON payload with this structure:

```json
{
  "type": "smschecker_config",
  "version": 1,
  "url": "https://your-domain.com",
  "apiKey": "sk_live_...",
  "secretKey": "sec_...",
  "deviceName": "Store Device"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Must be `"smschecker_config"` (the app validates this) |
| `version` | integer | Yes | Schema version, currently `1` |
| `url` | string | Yes | Base URL of the Laravel server (no trailing slash) |
| `apiKey` | string | Yes | The device API key |
| `secretKey` | string | Yes | The device secret key |
| `deviceName` | string | No | Display name for the device |

### Built-in Endpoints

The Laravel plugin provides two endpoints for QR code generation out of the box:

**HTML page with rendered QR code:**

```
GET /smschecker/device/{device}/qr
```

Returns a self-contained HTML page that renders the QR code client-side using the `qrcodejs` library. The page is styled with a dark theme and includes on-screen setup instructions. Protected by Laravel `auth` middleware.

**Raw JSON data:**

```
GET /smschecker/device/{device}/qr.json
```

Returns the raw JSON payload for the QR code. Useful for plugins that want to render the QR code using their own UI or a different QR library. Also protected by `auth` middleware.

### Generating QR Codes in a Plugin

If you are building a plugin (e.g., WooCommerce) that needs to display the QR code in its own admin panel:

1. Fetch the JSON from `/smschecker/device/{device}/qr.json` (authenticated request)
2. Encode the JSON string into a QR code using any QR library
3. Display the QR code to the admin user

Example using PHP with the `chillerlan/php-qrcode` library:

```php
use chillerlan\QRCode\QRCode;

$response = Http::withHeaders([
    'Cookie' => $sessionCookie,
])->get("https://your-domain.com/smschecker/device/{$deviceId}/qr.json");

$qrImage = (new QRCode)->render($response->body());
// $qrImage is a data URI you can use in an <img> tag
```

Or generate the JSON directly if you have access to the device credentials:

```php
$configPayload = json_encode([
    'type' => 'smschecker_config',
    'version' => 1,
    'url' => rtrim(config('app.url'), '/'),
    'apiKey' => $device->api_key,
    'secretKey' => $device->secret_key,
    'deviceName' => $device->device_name ?? 'SmsChecker Device',
], JSON_UNESCAPED_SLASHES);

// Pass $configPayload to your QR code renderer
```

---

## Part 6: Supported Banks

The system recognizes SMS from the following Thai banks by default (configured in `config/smschecker.php`):

| Code | Bank Name |
|------|-----------|
| `KBANK` | Kasikorn Bank |
| `SCB` | Siam Commercial Bank |
| `KTB` | Krungthai Bank |
| `BBL` | Bangkok Bank |
| `GSB` | Government Savings Bank |
| `BAY` | Bank of Ayudhya (Krungsri) |
| `TTB` | TMBThanachart Bank |
| `PROMPTPAY` | PromptPay |

The Android app uses pattern matching on the SMS body and sender to identify the bank. You can extend the bank list in the config file.

---

## Part 7: Available Artisan Commands

| Command | Description |
|---------|-------------|
| `php artisan smschecker:create-device "Name"` | Create a new device with API keys |
| `php artisan smschecker:create-device "Name" --user=1` | Create a device linked to a user |
| `php artisan smschecker:status` | Display system status (devices, notifications, amounts, nonces) |
| `php artisan smschecker:cleanup` | Clean up expired amounts, old nonces, and stale notifications |

---

## Troubleshooting

### App Not Receiving SMS

- **Permissions:** Open Android Settings > Apps > SMS Checker > Permissions. Confirm that SMS permission is granted.
- **Default SMS app conflict:** Some phones only deliver SMS to the default SMS app. SMS Checker uses a broadcast receiver with high priority (999), but some OEM skins may still block it. Try temporarily setting SMS Checker as the default SMS app to test.
- **Battery optimization:** On MIUI (Xiaomi), ColorOS (Oppo), One UI (Samsung), and similar skins, go to Settings > Battery > App battery management and set SMS Checker to "Unrestricted" or "No restrictions."
- **Auto-start permission:** Some devices require explicit auto-start permission. Check Settings > Apps > SMS Checker > Auto-start.
- **Boot receiver:** The app registers a `BOOT_COMPLETED` receiver to restart monitoring after reboot. Ensure this is not blocked by your device's security settings.

### Sync Failing (App Cannot Reach Server)

- **URL scheme:** The server URL must start with `https://`. HTTP connections will be rejected by Android's network security policy unless you have configured `network_security_config.xml` to allow cleartext traffic for your domain.
- **API key mismatch:** Verify that the API key entered in the app matches the one stored in the `sms_checker_devices` table. Run `php artisan smschecker:status` on the server to see registered devices.
- **Server logs:** Check `storage/logs/laravel.log` for authentication failures or decryption errors. Look for entries containing "SMS Payment" in the message.
- **Firewall:** Ensure your server allows incoming POST requests to `/api/v1/sms-payment/notify` from external IPs.
- **Timestamp drift:** The server rejects requests with timestamps older than 5 minutes (`SMSCHECKER_TIMESTAMP_TOLERANCE`). Ensure the phone's clock is accurate (use automatic time from your carrier).

### QR Code Not Scanning

- **Camera permission:** Ensure camera permission is granted to the app (Settings > Apps > SMS Checker > Permissions > Camera).
- **QR code size:** The QR code must be displayed at a reasonable size. If viewing on a small screen, zoom in so the code fills most of the camera viewfinder.
- **Lighting:** QR code scanning requires adequate lighting and contrast. The setup page uses a white background for the QR code to maximize contrast.
- **JSON validation:** The app validates that the scanned QR code contains `"type": "smschecker_config"`. If you are generating QR codes from a plugin, ensure the `type` field is present and correct.
- **Auth required:** The QR code page at `/smschecker/device/{device}/qr` requires you to be logged in to your Laravel application. If you see a login redirect, authenticate first.

### Unique Amount Not Matching

- **Exact amount required:** The customer must transfer the exact amount shown, including the decimal (satang) portion. For example, if the system generates 500.37, the customer must transfer 500.37, not 500.00 or 501.00.
- **Amount expired:** Unique amounts expire after 30 minutes by default (`SMSCHECKER_AMOUNT_EXPIRY`). Check `php artisan smschecker:status` to see active reservations. If expired, generate a new amount.
- **Slot exhaustion:** The system supports up to 99 unique decimal suffixes per base amount (`SMSCHECKER_MAX_PENDING`). If all slots are taken (very high traffic at the same price point), the generate function returns null. Wait for some to expire or increase the expiry window.
- **Credit vs debit:** Only credit (incoming) transactions are matched. The system ignores debit (outgoing) SMS notifications.

### TTS (Text-to-Speech) Voice Alerts Not Working

- **Device volume:** Ensure the phone's media volume is turned up and not on silent/vibrate mode.
- **TTS engine:** Android requires a TTS engine to be installed. Go to Settings > Accessibility > Text-to-speech output and ensure a TTS engine (e.g., Google TTS) is installed and the preferred language is set.
- **Thai language support:** If using Thai language for TTS alerts, download the Thai voice data pack from Settings > Accessibility > Text-to-speech output > Install voice data.
- **App in background:** TTS alerts may be suppressed if the phone's battery saver is active. Exempt the app from battery optimization.

### Database Issues

- **Migration failed:** Ensure your database user has CREATE TABLE permissions. The migration creates 4 tables. If one already exists, the migration will fail. Drop conflicting tables or skip them.
- **Duplicate nonce error:** This indicates a replay attack was prevented (or the same notification was sent twice). This is expected behavior. The nonce table is cleaned up automatically by `smschecker:cleanup`.
- **Cleanup not running:** If expired amounts and old nonces accumulate, verify that the scheduler is running (`php artisan schedule:work` or a cron job calling `php artisan schedule:run` every minute).
