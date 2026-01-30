<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\Log;

class OrderApproval extends Model
{
    protected $table = 'sms_checker_order_approvals';

    protected $fillable = [
        'notification_id',
        'matched_transaction_id',
        'device_id',
        'approval_status',
        'confidence',
        'approved_by',
        'approved_at',
        'rejected_at',
        'rejection_reason',
        'order_details_json',
        'synced_version',
    ];

    protected $casts = [
        'approved_at' => 'datetime',
        'rejected_at' => 'datetime',
        'order_details_json' => 'array',
        'matched_transaction_id' => 'integer',
        'synced_version' => 'integer',
    ];

    // --- Relationships ---

    public function notification()
    {
        return $this->belongsTo(SmsPaymentNotification::class, 'notification_id');
    }

    // --- Scopes ---

    public function scopePendingReview($query)
    {
        return $query->where('approval_status', 'pending_review');
    }

    public function scopeForDevice($query, string $deviceId)
    {
        return $query->where('device_id', $deviceId);
    }

    public function scopeApproved($query)
    {
        return $query->whereIn('approval_status', ['auto_approved', 'manually_approved']);
    }

    // --- Actions ---

    /**
     * Approve this order.
     *
     * @param string $approvedBy 'device', 'web_admin', or 'auto'
     */
    public function approve(string $approvedBy = 'device'): void
    {
        $this->update([
            'approval_status' => $approvedBy === 'auto' ? 'auto_approved' : 'manually_approved',
            'approved_by' => $approvedBy,
            'approved_at' => now(),
            'synced_version' => $this->synced_version + 1,
        ]);

        // Trigger host app callback to confirm the transaction
        $this->fireCallback('smschecker.on_order_approved');
    }

    /**
     * Reject this order.
     *
     * @param string $reason Rejection reason
     * @param string $rejectedBy 'device' or 'web_admin'
     */
    public function reject(string $reason = '', string $rejectedBy = 'device'): void
    {
        $this->update([
            'approval_status' => 'rejected',
            'approved_by' => $rejectedBy,
            'rejected_at' => now(),
            'rejection_reason' => $reason,
            'synced_version' => $this->synced_version + 1,
        ]);

        $this->fireCallback('smschecker.on_order_rejected');
    }

    /**
     * Fire a config callback safely.
     */
    private function fireCallback(string $configKey): void
    {
        try {
            $callback = config($configKey);
            if ($callback && is_callable($callback)) {
                call_user_func($callback, $this->matched_transaction_id, $this);
            }
        } catch (\Exception $e) {
            try {
                Log::error("OrderApproval callback error [{$configKey}]: " . $e->getMessage());
            } catch (\RuntimeException $ignored) {
                // Facade not available (testing context)
            }
        }
    }
}
