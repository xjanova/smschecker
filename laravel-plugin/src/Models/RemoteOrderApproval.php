<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class RemoteOrderApproval extends Model
{
    use HasFactory;

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
        'server_name',
        'synced_version',
    ];

    protected $casts = [
        'order_details_json' => 'array',
        'approved_at' => 'datetime',
        'rejected_at' => 'datetime',
        'synced_version' => 'integer',
    ];

    // =====================================================================
    // Relationships
    // =====================================================================

    public function notification()
    {
        return $this->belongsTo(SmsPaymentNotification::class, 'notification_id');
    }

    // =====================================================================
    // Scopes
    // =====================================================================

    public function scopePendingReview($query)
    {
        return $query->where('approval_status', 'pending_review');
    }

    public function scopeApproved($query)
    {
        return $query->whereIn('approval_status', ['auto_approved', 'manually_approved']);
    }

    // =====================================================================
    // Methods
    // =====================================================================

    /**
     * Create a RemoteOrderApproval from a notification and optional order data.
     */
    public static function createFromNotification(
        SmsPaymentNotification $notification,
        ?array $orderDetails = null,
        ?string $serverName = null
    ): self {
        return static::create([
            'notification_id' => $notification->id,
            'matched_transaction_id' => $notification->matched_transaction_id,
            'device_id' => $notification->device_id,
            'approval_status' => 'pending_review',
            'confidence' => 'high',
            'order_details_json' => $orderDetails,
            'server_name' => $serverName ?? config('app.name', 'Server'),
            'synced_version' => intval(round(microtime(true) * 1000)),
        ]);
    }

    /**
     * Create a RemoteOrderApproval directly from order data (for pending orders
     * that don't have a notification yet).
     */
    public static function createFromOrder(
        array $orderDetails,
        ?string $deviceId = null,
        ?string $serverName = null
    ): self {
        return static::create([
            'device_id' => $deviceId,
            'approval_status' => 'pending_review',
            'confidence' => 'high',
            'order_details_json' => $orderDetails,
            'server_name' => $serverName ?? config('app.name', 'Server'),
            'synced_version' => intval(round(microtime(true) * 1000)),
        ]);
    }

    /**
     * Approve this order.
     */
    public function approve(?string $approvedBy = 'device'): bool
    {
        if (!in_array($this->approval_status, ['pending_review'])) {
            return false;
        }

        $this->update([
            'approval_status' => $approvedBy === 'device' ? 'manually_approved' : 'auto_approved',
            'approved_by' => $approvedBy,
            'approved_at' => now(),
            'synced_version' => intval(round(microtime(true) * 1000)),
        ]);

        // Also update the notification status if linked
        if ($this->notification_id) {
            SmsPaymentNotification::where('id', $this->notification_id)
                ->update(['status' => 'confirmed']);
        }

        return true;
    }

    /**
     * Reject this order.
     */
    public function reject(string $reason = ''): bool
    {
        if (!in_array($this->approval_status, ['pending_review'])) {
            return false;
        }

        $this->update([
            'approval_status' => 'rejected',
            'rejection_reason' => $reason,
            'rejected_at' => now(),
            'synced_version' => intval(round(microtime(true) * 1000)),
        ]);

        // Update notification status
        if ($this->notification_id) {
            SmsPaymentNotification::where('id', $this->notification_id)
                ->update(['status' => 'rejected']);
        }

        return true;
    }

    /**
     * Format for API response (includes nested notification).
     */
    public function toApiResponse(): array
    {
        $data = [
            'id' => $this->id,
            'notification_id' => $this->notification_id,
            'matched_transaction_id' => $this->matched_transaction_id,
            'device_id' => $this->device_id,
            'approval_status' => $this->approval_status,
            'confidence' => $this->confidence,
            'approved_by' => $this->approved_by,
            'approved_at' => $this->approved_at?->toIso8601String(),
            'rejected_at' => $this->rejected_at?->toIso8601String(),
            'rejection_reason' => $this->rejection_reason,
            'order_details_json' => $this->order_details_json,
            'server_name' => $this->server_name,
            'synced_version' => $this->synced_version,
            'created_at' => $this->created_at?->toIso8601String(),
            'updated_at' => $this->updated_at?->toIso8601String(),
        ];

        // Include notification details if loaded
        if ($this->relationLoaded('notification') && $this->notification) {
            $notif = $this->notification;
            $data['notification'] = [
                'id' => $notif->id,
                'bank' => $notif->bank,
                'type' => $notif->type,
                'amount' => number_format((float) $notif->amount, 2, '.', ''),
                'sms_timestamp' => $notif->sms_timestamp?->toIso8601String(),
                'sender_or_receiver' => $notif->sender_or_receiver,
            ];
        } else {
            $data['notification'] = null;
        }

        return $data;
    }

    /**
     * Find order by bill_reference (order_number) or by id.
     */
    public static function findByIdentifier(string $identifier): ?self
    {
        // First try by id (numeric)
        if (is_numeric($identifier)) {
            $order = static::find((int) $identifier);
            if ($order) return $order;
        }

        // Then try by order_number in order_details_json
        return static::whereJsonContains('order_details_json->order_number', $identifier)->first()
            ?? static::where('id', $identifier)->first();
    }
}
