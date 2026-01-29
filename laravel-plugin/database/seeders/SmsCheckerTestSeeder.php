<?php

namespace Database\Seeders;

use App\Models\SmsCheckerDevice;
use Illuminate\Database\Seeder;

class SmsCheckerTestSeeder extends Seeder
{
    /**
     * Seed a test device for development.
     * DO NOT use in production.
     */
    public function run(): void
    {
        if (app()->environment('production')) {
            $this->command->error('Cannot run test seeder in production!');
            return;
        }

        $device = SmsCheckerDevice::firstOrCreate(
            ['device_id' => 'SMSCHK-TEST0001'],
            [
                'device_name' => 'Test Device (Development)',
                'api_key' => 'test_api_key_do_not_use_in_production_1234567890abcdef1234567890abcdef',
                'secret_key' => 'test_secret_key_do_not_use_prod_1234567890abcdef1234567890abcdef',
                'status' => 'active',
                'platform' => 'android',
                'app_version' => '1.0.0-dev',
            ]
        );

        $this->command->info("Test device created: {$device->device_id}");
        $this->command->warn('API Key: test_api_key_do_not_use_in_production_1234567890abcdef1234567890abcdef');
        $this->command->warn('Secret:  test_secret_key_do_not_use_prod_1234567890abcdef1234567890abcdef');
    }
}
