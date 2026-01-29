<?php

namespace App\Console\Commands;

use App\Models\SmsCheckerDevice;
use App\Models\SmsPaymentNotification;
use App\Models\UniquePaymentAmount;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

class StatusCommand extends Command
{
    protected $signature = 'smschecker:status';

    protected $description = 'Show SMS Payment Checker system status';

    public function handle(): int
    {
        $this->info('');
        $this->info('╔══════════════════════════════════════╗');
        $this->info('║    SMS Payment Checker Status        ║');
        $this->info('╚══════════════════════════════════════╝');
        $this->info('');

        // Devices
        $totalDevices = SmsCheckerDevice::count();
        $activeDevices = SmsCheckerDevice::where('status', 'active')->count();
        $recentDevices = SmsCheckerDevice::where('last_active_at', '>=', now()->subHours(24))->count();

        $this->info('📱 Devices:');
        $this->table(
            ['Metric', 'Count'],
            [
                ['Total devices', $totalDevices],
                ['Active devices', $activeDevices],
                ['Active last 24h', $recentDevices],
            ]
        );

        // Notifications
        $totalNotifications = SmsPaymentNotification::count();
        $pendingNotifications = SmsPaymentNotification::where('status', 'pending')->count();
        $matchedNotifications = SmsPaymentNotification::where('status', 'matched')->count();
        $todayNotifications = SmsPaymentNotification::whereDate('created_at', today())->count();

        $this->info('📨 Notifications:');
        $this->table(
            ['Metric', 'Count'],
            [
                ['Total notifications', $totalNotifications],
                ['Pending (unmatched)', $pendingNotifications],
                ['Matched', $matchedNotifications],
                ['Today', $todayNotifications],
            ]
        );

        // Unique Amounts
        $reservedAmounts = UniquePaymentAmount::where('status', 'reserved')
            ->where('expires_at', '>', now())
            ->count();
        $usedAmounts = UniquePaymentAmount::where('status', 'used')->count();

        $this->info('💰 Unique Amounts:');
        $this->table(
            ['Metric', 'Count'],
            [
                ['Active reservations', $reservedAmounts],
                ['Used (matched)', $usedAmounts],
            ]
        );

        // Nonces
        $nonceCount = DB::table('sms_payment_nonces')->count();
        $this->info("🔑 Nonces stored: {$nonceCount}");

        $this->info('');
        $this->info('Version: ' . trim(file_get_contents(base_path('VERSION')) ?: '1.0.0'));
        $this->info('');

        return Command::SUCCESS;
    }
}
