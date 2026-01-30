<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use App\Models\SmsCheckerDevice;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class QrConfigController extends Controller
{
    /**
     * Show the device QR code page for easy Android app configuration.
     *
     * GET /smschecker/device/{device}/qr
     */
    public function show(SmsCheckerDevice $device)
    {
        $configPayload = json_encode([
            'type' => 'smschecker_config',
            'version' => 1,
            'url' => rtrim(config('app.url'), '/'),
            'apiKey' => $device->api_key,
            'secretKey' => $device->secret_key,
            'deviceName' => $device->device_name ?? 'SmsChecker Device',
        ], JSON_UNESCAPED_SLASHES);

        return view('smschecker.qr-setup', [
            'device' => $device,
            'configJson' => $configPayload,
        ]);
    }

    /**
     * Return QR code data as JSON (API endpoint alternative).
     *
     * GET /smschecker/device/{device}/qr.json
     */
    public function json(SmsCheckerDevice $device): JsonResponse
    {
        return response()->json([
            'type' => 'smschecker_config',
            'version' => 1,
            'url' => rtrim(config('app.url'), '/'),
            'apiKey' => $device->api_key,
            'secretKey' => $device->secret_key,
            'deviceName' => $device->device_name ?? 'SmsChecker Device',
        ]);
    }
}
