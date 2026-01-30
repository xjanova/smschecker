<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('sms_checker_order_approvals', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('notification_id');
            $table->unsignedBigInteger('matched_transaction_id')->nullable();
            $table->string('device_id');
            $table->enum('approval_status', [
                'pending_review',
                'auto_approved',
                'manually_approved',
                'rejected',
                'expired',
            ])->default('pending_review');
            $table->enum('confidence', ['high', 'ambiguous'])->default('high');
            $table->string('approved_by')->nullable(); // device, web_admin, auto
            $table->timestamp('approved_at')->nullable();
            $table->timestamp('rejected_at')->nullable();
            $table->string('rejection_reason')->nullable();
            $table->text('order_details_json')->nullable();
            $table->unsignedBigInteger('synced_version')->default(0);
            $table->timestamps();

            $table->index(['device_id', 'approval_status']);
            $table->index('matched_transaction_id');
            $table->index('synced_version');
            $table->foreign('notification_id')
                ->references('id')
                ->on('sms_payment_notifications')
                ->onDelete('cascade');
        });

        Schema::table('sms_checker_devices', function (Blueprint $table) {
            $table->enum('approval_mode', ['auto', 'manual', 'smart'])->default('auto')->after('status');
        });

        Schema::table('sms_payment_notifications', function (Blueprint $table) {
            $table->unsignedBigInteger('approval_id')->nullable()->after('matched_transaction_id');
        });
    }

    public function down(): void
    {
        Schema::table('sms_payment_notifications', function (Blueprint $table) {
            $table->dropColumn('approval_id');
        });

        Schema::table('sms_checker_devices', function (Blueprint $table) {
            $table->dropColumn('approval_mode');
        });

        Schema::dropIfExists('sms_checker_order_approvals');
    }
};
