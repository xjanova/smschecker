<?php

namespace App\Console\Commands;

use App\Models\SmsCheckerDevice;
use Illuminate\Console\Command;

class CreateDeviceCommand extends Command
{
    protected $signature = 'smschecker:create-device
                            {name : Device display name}
                            {--user= : Associate with user ID}';

    protected $description = 'Create a new SMS Checker device with API keys';

    public function handle(): int
    {
        $name = $this->argument('name');
        $userId = $this->option('user');

        $apiKey = SmsCheckerDevice::generateApiKey();
        $secretKey = SmsCheckerDevice::generateSecretKey();
        $deviceId = 'SMSCHK-' . strtoupper(substr(bin2hex(random_bytes(4)), 0, 8));

        $device = SmsCheckerDevice::create([
            'device_id' => $deviceId,
            'device_name' => $name,
            'api_key' => $apiKey,
            'secret_key' => $secretKey,
            'status' => 'active',
            'user_id' => $userId,
        ]);

        $this->info('');
        $this->info('============================================');
        $this->info('  SMS Checker Device Created Successfully');
        $this->info('============================================');
        $this->info('');
        $this->table(
            ['Field', 'Value'],
            [
                ['Device ID', $device->device_id],
                ['Device Name', $device->device_name],
                ['Status', $device->status],
                ['API Key', $apiKey],
                ['Secret Key', $secretKey],
            ]
        );
        $this->info('');
        $this->warn('⚠️  SAVE THESE KEYS! They cannot be retrieved later.');
        $this->warn('    Enter these in the Android app Settings → Add Server');
        $this->info('');

        return Command::SUCCESS;
    }
}
