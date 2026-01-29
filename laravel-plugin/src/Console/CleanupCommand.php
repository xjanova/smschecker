<?php

namespace App\Console\Commands;

use App\Services\SmsPaymentService;
use Illuminate\Console\Command;

class CleanupCommand extends Command
{
    protected $signature = 'smschecker:cleanup';

    protected $description = 'Clean up expired unique amounts, old nonces, and stale notifications';

    public function handle(SmsPaymentService $service): int
    {
        $this->info('Running SMS Checker cleanup...');

        $service->cleanup();

        $this->info('✅ Cleanup completed:');
        $this->info('  - Expired unique amounts marked');
        $this->info('  - Old nonces removed (>24h)');
        $this->info('  - Stale pending notifications expired (>7d)');

        return Command::SUCCESS;
    }
}
