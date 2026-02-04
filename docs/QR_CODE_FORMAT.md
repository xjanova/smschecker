# QR Code Data Format Specification

This document describes the QR code data format used by the SMS Payment Checker Android app for server configuration.

## Overview

The SMS Payment Checker Android app scans QR codes to configure server connections. The QR code must contain a JSON payload with specific fields and format.

## Required JSON Structure

```json
{
  "type": "smschecker_config",
  "version": 2,
  "url": "https://example.com",
  "apiKey": "your-api-key-here-min-16-chars",
  "secretKey": "your-secret-key-here-min-16-chars",
  "deviceId": "SMSCHK-XXXXXXXX",
  "deviceName": "Device Name"
}
```

## Field Specifications

### type (Required)
- **Value**: Must be exactly `"smschecker_config"`
- **Important**: No underscore between "sms" and "checker"
- **Wrong**: `"sms_checker_config"` (will be rejected)

### version (Required)
- **Type**: Integer
- **Current Version**: `2`
- **Purpose**: Allows future format changes while maintaining backward compatibility

### url (Required)
- **Type**: String (URL)
- **Format**: Base URL only, without API paths
- **Correct**: `"https://example.com"` or `"https://shop.example.com"`
- **Wrong**: `"https://example.com/api/v1/sms-payment"` (Android app adds paths automatically)
- **Security**: Must be HTTPS in production (HTTP allowed only for localhost in debug builds)

### apiKey (Required)
- **Type**: String
- **Minimum Length**: 16 characters
- **Format**: camelCase key name
- **Purpose**: Authentication token for API requests

### secretKey (Required)
- **Type**: String
- **Minimum Length**: 16 characters
- **Format**: camelCase key name
- **Purpose**: Used for HMAC signature generation

### deviceId (Required)
- **Type**: String
- **Format**: camelCase key name
- **Example**: `"SMSCHK-268CF109"`
- **Purpose**: Unique identifier for the device registration

### deviceName (Required)
- **Type**: String
- **Format**: camelCase key name
- **Purpose**: Human-readable name for the device

## Android App API Endpoints

The Android app automatically appends these paths to the base URL:

| Endpoint | Method | Path |
|----------|--------|------|
| Notify Transaction | POST | `/api/v1/sms-payment/notify` |
| Check Status | GET | `/api/v1/sms-payment/status` |
| Register Device | POST | `/api/v1/sms-payment/register-device` |
| Get Orders | GET | `/api/v1/sms-payment/orders` |
| Approve Order | POST | `/api/v1/sms-payment/orders/{id}/approve` |
| Reject Order | POST | `/api/v1/sms-payment/orders/{id}/reject` |
| Bulk Approve | POST | `/api/v1/sms-payment/orders/bulk-approve` |
| Sync Orders | GET | `/api/v1/sms-payment/orders/sync` |
| Device Settings | GET/PUT | `/api/v1/sms-payment/device-settings` |
| Dashboard Stats | GET | `/api/v1/sms-payment/dashboard-stats` |

## Implementation Examples

### Laravel (xmanstudio)

```php
// app/Http/Controllers/Admin/SmsPaymentController.php

public function showDevice(SmsCheckerDevice $device)
{
    // Config for QR Code - must match Android app expected format (camelCase)
    // URL should be base URL only - Android app adds /api/v1/sms-payment/* paths
    $config = [
        'type' => 'smschecker_config',
        'version' => 2,
        'url' => config('app.url'),  // Base URL only!
        'apiKey' => $device->api_key,
        'secretKey' => $device->secret_key,
        'deviceId' => $device->device_id,
        'deviceName' => $device->name,
    ];

    return view('admin.sms-payment.device-show', compact('device', 'config'));
}
```

### WordPress Plugin

```php
// includes/class-spc-device-manager.php

public static function get_qr_data( $device_id ) {
    $device = self::get_device( $device_id );
    if ( ! $device ) {
        return false;
    }

    return array(
        'type'       => 'smschecker_config',  // No underscore between "sms" and "checker"
        'version'    => 2,
        'url'        => home_url(),           // Base URL only - Android app adds API paths
        'apiKey'     => $device->api_key,
        'secretKey'  => $device->secret_key,
        'deviceName' => $device->device_name,
        'deviceId'   => $device->device_id,
    );
}
```

### JavaScript (for QR Code generation)

```javascript
// Using qrcodejs library (browser-compatible)
var qrData = JSON.stringify({
    type: 'smschecker_config',  // No underscore!
    version: 2,
    url: 'https://example.com', // Base URL only!
    apiKey: device.api_key,
    secretKey: device.secret_key,
    deviceName: device.device_name,
    deviceId: device.device_id
});

new QRCode(container, {
    text: qrData,
    width: 250,
    height: 250,
    colorDark: '#000000',
    colorLight: '#ffffff',
    correctLevel: QRCode.CorrectLevel.H
});
```

## Validation Rules in Android App

The Android app (`QrScannerScreen.kt`) performs these validations:

1. **JSON Parse**: Must be valid JSON or base64-encoded JSON
2. **Type Check**: `type` field must equal `"smschecker_config"` exactly
3. **Required Fields**: `url`, `apiKey`, `secretKey` must not be blank
4. **URL Security**: Must start with `https://` (except localhost in debug)
5. **Key Length**: Both `apiKey` and `secretKey` must be at least 16 characters

## Common Mistakes

| Mistake | Correct |
|---------|---------|
| `type: "sms_checker_config"` | `type: "smschecker_config"` |
| `url: "https://example.com/api/v1/sms-payment"` | `url: "https://example.com"` |
| `api_key` (snake_case) | `apiKey` (camelCase) |
| `secret_key` (snake_case) | `secretKey` (camelCase) |
| `device_id` (snake_case) | `deviceId` (camelCase) |
| `device_name` (snake_case) | `deviceName` (camelCase) |
| `server_url` | `url` |

## QR Code Library Recommendations

### Browser (JavaScript)
- **Recommended**: `qrcodejs@1.0.0` via CDN
- **CDN**: `https://cdn.jsdelivr.net/npm/qrcodejs@1.0.0/qrcode.min.js`
- **Note**: Do NOT use `qrcode@1.5.3` (Node.js library, not browser-compatible)

### PHP (Server-side)
- **Recommended**: `simplesoftwareio/simple-qrcode` for Laravel
- Alternative: Generate QR via JavaScript on client-side

## Troubleshooting

### QR Code Not Scanning
1. Check JSON format is valid
2. Verify `type` is exactly `"smschecker_config"`
3. Ensure URL is base URL only (no API paths)
4. Confirm all keys use camelCase

### "QR Code read but not smschecker_config format"
- The `type` field is wrong or missing
- Check for typos: `smschecker_config` not `sms_checker_config`

### Connection Failed After Scanning
- Verify server URL is correct and accessible
- Check HTTPS certificate is valid
- Ensure API endpoints are properly configured
- Verify `apiKey` and `secretKey` match server records

## Reference

- Android App Source: `app/src/main/java/com/thaiprompt/smschecker/ui/qrscanner/QrScannerScreen.kt`
- API Service: `app/src/main/java/com/thaiprompt/smschecker/data/api/PaymentApiService.kt`
