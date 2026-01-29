# Setup Guide

## Prerequisites

| Component | Requirement |
|-----------|------------|
| **Android App** | Android Studio 2023.2+, JDK 17+, Android device (API 26+) |
| **Laravel Plugin** | PHP 8.1+, Laravel 10/11, MySQL/PostgreSQL, Composer 2.x |

---

## Part 1: Laravel Plugin Installation

### Step 1: Copy Files

```bash
# From this repository root
cp -r laravel-plugin/src/Http/Controllers/Api/V1/SmsPaymentController.php \
      your-laravel/app/Http/Controllers/Api/V1/

cp -r laravel-plugin/src/Http/Middleware/VerifySmsCheckerDevice.php \
      your-laravel/app/Http/Middleware/

cp -r laravel-plugin/src/Models/SmsCheckerDevice.php \
      laravel-plugin/src/Models/SmsPaymentNotification.php \
      laravel-plugin/src/Models/UniquePaymentAmount.php \
      your-laravel/app/Models/

cp -r laravel-plugin/src/Services/SmsPaymentService.php \
      your-laravel/app/Services/

cp -r laravel-plugin/src/Console/*.php \
      your-laravel/app/Console/Commands/

cp laravel-plugin/config/smschecker.php your-laravel/config/
cp laravel-plugin/database/migrations/*.php your-laravel/database/migrations/
cp laravel-plugin/routes/sms_payment_api.php your-laravel/routes/
```

### Step 2: Register Routes

Add to `routes/api.php`:

```php
// SMS Payment Checker Routes
require __DIR__ . '/sms_payment_api.php';
```

### Step 3: Register Middleware

**Laravel 11** - Add to `bootstrap/app.php`:

```php
->withMiddleware(function (Middleware $middleware) {
    $middleware->alias([
        // ... existing
        'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
    ]);
})
```

**Laravel 10** - Add to `app/Http/Kernel.php`:

```php
protected $middlewareAliases = [
    // ... existing
    'smschecker' => \App\Http\Middleware\VerifySmsCheckerDevice::class,
];
```

### Step 4: Run Migration

```bash
php artisan migrate
```

This creates 4 tables:
- `sms_checker_devices` - Device management
- `sms_payment_notifications` - SMS notification records
- `unique_payment_amounts` - Decimal amount reservations
- `sms_payment_nonces` - Replay attack prevention

### Step 5: Create Device

```bash
php artisan smschecker:create-device "My Phone"

# Or with user association:
php artisan smschecker:create-device "My Phone" --user=1
```

Save the **API Key** and **Secret Key** - you'll need them for the Android app.

### Step 6: Environment Variables (Optional)

Add to `.env`:

```env
SMSCHECKER_TIMESTAMP_TOLERANCE=300
SMSCHECKER_AMOUNT_EXPIRY=30
SMSCHECKER_RATE_LIMIT=30
SMSCHECKER_AUTO_CONFIRM=true
SMSCHECKER_NOTIFY_ON_MATCH=true
SMSCHECKER_LOG_LEVEL=info
```

### Step 7: Schedule Cleanup (Recommended)

Add to `routes/console.php` or scheduler:

```php
Schedule::command('smschecker:cleanup')->hourly();
```

### Step 8: Verify Installation

```bash
php artisan smschecker:status
```

---

## Part 2: Android App Installation

### Step 1: Open Project

1. Open Android Studio
2. File → Open → Select `android-app/` directory
3. Wait for Gradle sync to complete

### Step 2: Build

```
Build → Make Project (Ctrl+F9)
```

### Step 3: Install on Device

1. Connect Android device via USB
2. Enable Developer Options + USB Debugging on device
3. Run → Run 'app' (Shift+F10)

### Step 4: Grant Permissions

The app will request:
- **SMS** - Read incoming bank SMS
- **Notifications** - Show transaction alerts

### Step 5: Configure Server Connection

1. Open app → **Settings** tab
2. Tap **Add Server**
3. Fill in:
   - **Server Name**: Your server name (e.g., "Thaiprompt Main")
   - **Server URL**: `https://your-domain.com`
   - **API Key**: From Step 5 of Laravel setup
   - **Secret Key**: From Step 5 of Laravel setup
4. Check "Set as default server"
5. Tap **Save**

### Step 6: Enable Monitoring

- Toggle **SMS Monitoring** to Active
- The app will now automatically detect bank SMS

---

## Integration with Checkout

### Generate Unique Amount During Checkout

```php
use App\Services\SmsPaymentService;

class CheckoutController extends Controller
{
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
            return back()->with('error', 'ไม่สามารถสร้างยอดชำระได้ กรุณาลองใหม่');
        }

        return view('checkout.bank-transfer', [
            'order' => $order,
            'paymentAmount' => $uniqueAmount->unique_amount, // e.g., 500.37
            'displayAmount' => '฿' . number_format($uniqueAmount->unique_amount, 2),
            'expiresAt' => $uniqueAmount->expires_at,
        ]);
    }
}
```

### Payment Flow

```
1. Customer places order (฿500.00)
2. System generates unique amount (฿500.37)
3. Customer opens bank app, transfers ฿500.37
4. Bank sends SMS to customer's phone
5. SMS Checker app reads SMS → detects ฿500.37 credit
6. App encrypts & sends to Laravel API
7. API decrypts → finds matching unique amount ฿500.37
8. Payment auto-confirmed ✅
9. Order status updated to "paid"
```

---

## Troubleshooting

### App not receiving SMS
- Check SMS permission is granted
- Check if default SMS app is blocking
- Some Android skins (MIUI, ColorOS) require manual battery optimization exemption

### Sync failing
- Verify server URL starts with `https://`
- Check API key matches device on server
- Check server logs: `storage/logs/laravel.log`

### Unique amount not matching
- Ensure customer transfers exact amount (including decimals)
- Check amount hasn't expired (default 30 min)
- Run `php artisan smschecker:status` to see pending amounts
