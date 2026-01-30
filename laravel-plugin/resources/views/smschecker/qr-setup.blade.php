<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SMS Checker - Device Setup</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #121212;
            color: #fff;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .card {
            background: #1E1E2E;
            border-radius: 20px;
            padding: 36px;
            max-width: 420px;
            width: 100%;
            text-align: center;
            box-shadow: 0 8px 32px rgba(0,0,0,0.4);
        }
        .logo-text {
            font-size: 1.6em;
            font-weight: 700;
            margin-bottom: 4px;
        }
        .gold { color: #D4AF37; }
        .subtitle {
            color: #888;
            font-size: 0.85em;
            margin-bottom: 24px;
        }
        .device-badge {
            display: inline-block;
            background: rgba(212, 175, 55, 0.15);
            color: #D4AF37;
            padding: 6px 16px;
            border-radius: 20px;
            font-size: 0.9em;
            font-weight: 600;
            margin-bottom: 24px;
        }
        .qr-container {
            background: #fff;
            border-radius: 16px;
            padding: 24px;
            display: inline-block;
            margin: 0 auto 24px;
        }
        #qrcode {
            display: inline-block;
        }
        .warning {
            background: rgba(255, 152, 0, 0.1);
            border: 1px solid rgba(255, 152, 0, 0.3);
            border-radius: 12px;
            padding: 12px 16px;
            margin-bottom: 20px;
            font-size: 0.8em;
            color: #FFB74D;
            text-align: left;
        }
        .warning-icon { margin-right: 6px; }
        .instructions {
            text-align: left;
            margin-top: 20px;
        }
        .instructions h3 {
            font-size: 0.95em;
            margin-bottom: 12px;
            color: #D4AF37;
        }
        .instructions ol {
            padding-left: 20px;
            color: #ccc;
            font-size: 0.88em;
            line-height: 1.8;
        }
        .footer {
            margin-top: 24px;
            padding-top: 16px;
            border-top: 1px solid rgba(255,255,255,0.08);
            font-size: 0.75em;
            color: #555;
        }
        .footer .brand { color: rgba(212, 175, 55, 0.5); }
    </style>
</head>
<body>
    <div class="card">
        <div class="logo-text">
            <span class="gold">SMS Checker</span> Setup
        </div>
        <p class="subtitle">Scan to auto-configure your Android app</p>

        <div class="device-badge">
            {{ $device->device_name ?? $device->device_id }}
        </div>

        <div class="qr-container">
            <div id="qrcode"></div>
        </div>

        <div class="warning">
            <span class="warning-icon">&#9888;</span>
            This QR code contains your device's API keys. Do not share or screenshot this page.
        </div>

        <div class="instructions">
            <h3>Setup Instructions</h3>
            <ol>
                <li>Open the <strong>SMS Checker</strong> app on your Android device</li>
                <li>Go to <strong>Settings</strong> tab</li>
                <li>Tap the <strong>"QR"</strong> button</li>
                <li>Point your camera at this QR code</li>
                <li>The server will be added automatically</li>
            </ol>
        </div>

        <div class="footer">
            <span class="brand">Produced by xman studio</span> &middot; xman4289.com
        </div>
    </div>

    <!-- Client-side QR code generation (no PHP dependency needed) -->
    <script src="https://cdn.jsdelivr.net/npm/qrcodejs@1.0.0/qrcode.min.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            var configData = @json($configJson);
            new QRCode(document.getElementById('qrcode'), {
                text: configData,
                width: 256,
                height: 256,
                colorDark: '#000000',
                colorLight: '#ffffff',
                correctLevel: QRCode.CorrectLevel.M
            });
        });
    </script>
</body>
</html>
