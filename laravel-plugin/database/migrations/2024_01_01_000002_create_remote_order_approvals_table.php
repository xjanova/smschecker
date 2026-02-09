<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Remote order approvals table - links SMS notifications to orders for device approval
        Schema::create('remote_order_approvals', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('notification_id')->nullable();
            $table->unsignedBigInteger('matched_transaction_id')->nullable();
            $table->string('device_id')->nullable();
            $table->enum('approval_status', [
                'pending_review',
                'auto_approved',
                'manually_approved',
                'rejected',
                'expired',
                'cancelled',
                'deleted',
            ])->default('pending_review');
            $table->string('confidence')->default('high'); // high, ambiguous
            $table->string('approved_by')->nullable();
            $table->timestamp('approved_at')->nullable();
            $table->timestamp('rejected_at')->nullable();
            $table->string('rejection_reason')->nullable();
            $table->json('order_details_json')->nullable();
            $table->string('server_name')->nullable();
            $table->unsignedBigInteger('synced_version')->default(0);
            $table->timestamps();

            $table->index('notification_id');
            $table->index('matched_transaction_id');
            $table->index('device_id');
            $table->index('approval_status');
            $table->index('synced_version');
        });

        // Add approval_mode and fcm_token to sms_checker_devices if not exists
        if (!Schema::hasColumn('sms_checker_devices', 'approval_mode')) {
            Schema::table('sms_checker_devices', function (Blueprint $table) {
                $table->enum('approval_mode', ['auto', 'manual', 'smart'])->default('auto')->after('status');
                $table->string('fcm_token')->nullable()->after('ip_address');
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('remote_order_approvals');

        if (Schema::hasColumn('sms_checker_devices', 'approval_mode')) {
            Schema::table('sms_checker_devices', function (Blueprint $table) {
                $table->dropColumn(['approval_mode', 'fcm_token']);
            });
        }
    }
};
