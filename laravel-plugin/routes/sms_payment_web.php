<?php

/**
 * SMS Payment Gateway Web Routes
 *
 * Add these routes to your main routes/web.php file:
 *
 * require __DIR__ . '/sms_payment_web.php';
 *
 * Or copy the contents into your existing web.php
 */

use App\Http\Controllers\Web\QrConfigController;
use Illuminate\Support\Facades\Route;

// QR Code setup page for device configuration
// Protected by Laravel auth middleware (admin must be logged in)
Route::middleware(['auth'])->prefix('smschecker')->group(function () {

    // Show QR code page for a device (scan with Android app to auto-configure)
    Route::get('/device/{device}/qr', [QrConfigController::class, 'show'])
        ->name('smschecker.device.qr');

    // JSON endpoint for programmatic access
    Route::get('/device/{device}/qr.json', [QrConfigController::class, 'json'])
        ->name('smschecker.device.qr.json');
});
