<?php

namespace App\Services;

use App\Models\OrderApproval;
use App\Models\SmsCheckerDevice;
use App\Models\SmsPaymentNotification;
use App\Models\UniquePaymentAmount;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

class SmsPaymentService
{
    /**
     * Process an incoming SMS payment notification from the Android app.
     *
     * @param array $payload Decrypted payload from the app
     * @param SmsCheckerDevice $device The authenticated device
     * @param string $ipAddress Client IP
     * @return array Result with success/failure info
     */
    public function processNotification(array $payload, SmsCheckerDevice $device, string $ipAddress): array
    {
        return DB::transaction(function () use ($payload, $device, $ipAddress) {
            // Check for duplicate nonce (replay attack prevention)
            $existingNonce = DB::table('sms_payment_nonces')
                ->where('nonce', $payload['nonce'])
                ->exists();

            if ($existingNonce) {
                Log::warning('SMS Payment: Duplicate nonce detected', [
                    'nonce' => $payload['nonce'],
                    'device_id' => $device->device_id,
                ]);
                return [
                    'success' => false,
                    'message' => 'Duplicate request (nonce already used)',
                ];
            }

            // Record nonce
            DB::table('sms_payment_nonces')->insert([
                'nonce' => $payload['nonce'],
                'device_id' => $device->device_id,
                'used_at' => now(),
                'created_at' => now(),
                'updated_at' => now(),
            ]);

            // Create notification record
            $notification = SmsPaymentNotification::create([
                'bank' => $payload['bank'],
                'type' => $payload['type'],
                'amount' => $payload['amount'],
                'account_number' => $payload['account_number'] ?? '',
                'sender_or_receiver' => $payload['sender_or_receiver'] ?? '',
                'reference_number' => $payload['reference_number'] ?? '',
                'sms_timestamp' => date('Y-m-d H:i:s', $payload['sms_timestamp'] / 1000),
                'device_id' => $device->device_id,
                'nonce' => $payload['nonce'],
                'status' => 'pending',
                'raw_payload' => json_encode($payload),
                'ip_address' => $ipAddress,
            ]);

            // Update device activity
            $device->update([
                'last_active_at' => now(),
                'ip_address' => $ipAddress,
            ]);

            // Attempt auto-match for credit transactions
            $matched = false;
            $approvalId = null;
            $approvalStatus = null;

            if ($notification->type === 'credit') {
                $matched = $notification->attemptMatch();
            }

            // If matched, route through the approval system
            if ($matched) {
                $approval = $this->createApprovalRecord($notification, $device);
                $approvalId = $approval->id;
                $this->handleApprovalMode($approval, $device);
                $approvalStatus = $approval->fresh()->approval_status;
            }

            Log::info('SMS Payment notification processed', [
                'notification_id' => $notification->id,
                'bank' => $notification->bank,
                'type' => $notification->type,
                'amount' => $notification->amount,
                'matched' => $matched,
                'approval_id' => $approvalId,
                'approval_status' => $approvalStatus,
            ]);

            return [
                'success' => true,
                'message' => $matched ? 'Payment matched' : 'Notification recorded',
                'data' => [
                    'notification_id' => $notification->id,
                    'status' => $notification->status,
                    'matched' => $matched,
                    'matched_transaction_id' => $notification->matched_transaction_id,
                    'approval_id' => $approvalId,
                    'approval_status' => $approvalStatus,
                ],
            ];
        });
    }

    /**
     * Create an OrderApproval record for a matched notification.
     */
    private function createApprovalRecord(SmsPaymentNotification $notification, SmsCheckerDevice $device): OrderApproval
    {
        $orderDetails = $this->resolveOrderDetails($notification->matched_transaction_id);
        $confidence = $this->assessConfidence($notification);

        $approval = OrderApproval::create([
            'notification_id' => $notification->id,
            'matched_transaction_id' => $notification->matched_transaction_id,
            'device_id' => $device->device_id,
            'approval_status' => 'pending_review',
            'confidence' => $confidence,
            'order_details_json' => $orderDetails,
        ]);

        $notification->update(['approval_id' => $approval->id]);

        return $approval;
    }

    /**
     * Route through approval mode: auto, manual, or smart.
     */
    private function handleApprovalMode(OrderApproval $approval, SmsCheckerDevice $device): void
    {
        $mode = $device->approval_mode ?? config('smschecker.default_approval_mode', 'auto');

        switch ($mode) {
            case 'auto':
                $approval->approve('auto');
                break;

            case 'manual':
                // Leave as pending_review
                break;

            case 'smart':
                if ($approval->confidence === 'high') {
                    $approval->approve('auto');
                }
                // ambiguous stays as pending_review
                break;
        }
    }

    /**
     * Assess confidence for smart mode.
     * If multiple active UniquePaymentAmounts share the same base_amount, mark as ambiguous.
     */
    private function assessConfidence(SmsPaymentNotification $notification): string
    {
        $windowMinutes = config('smschecker.smart_mode.ambiguous_window_minutes', 10);
        $threshold = config('smschecker.smart_mode.ambiguous_amount_threshold', 2);

        $activeCount = UniquePaymentAmount::where('status', 'reserved')
            ->where('expires_at', '>', now())
            ->where('base_amount', floor((float) $notification->amount))
            ->where('created_at', '>=', now()->subMinutes($windowMinutes))
            ->count();

        return $activeCount >= $threshold ? 'ambiguous' : 'high';
    }

    /**
     * Resolve order details from the host app via config callback.
     */
    private function resolveOrderDetails(?int $transactionId): ?array
    {
        if (!$transactionId) {
            return null;
        }

        $resolver = config('smschecker.order_details_resolver');
        if ($resolver && is_callable($resolver)) {
            try {
                return call_user_func($resolver, $transactionId);
            } catch (\Exception $e) {
                Log::warning('OrderApproval: Failed to resolve order details', [
                    'transaction_id' => $transactionId,
                    'error' => $e->getMessage(),
                ]);
            }
        }

        return null;
    }

    /**
     * Decrypt the encrypted payload from the app.
     *
     * @param string $encryptedData Base64 encoded AES-256-GCM encrypted data
     * @param string $secretKey The device's secret key
     * @return array|null Decrypted payload or null on failure
     */
    public function decryptPayload(string $encryptedData, string $secretKey): ?array
    {
        try {
            $combined = base64_decode($encryptedData);
            if ($combined === false || strlen($combined) < 12) {
                return null;
            }

            $ivLength = 12; // GCM IV is 12 bytes
            $tagLength = 16; // GCM tag is 16 bytes

            $iv = substr($combined, 0, $ivLength);
            $cipherTextWithTag = substr($combined, $ivLength);

            // Separate ciphertext and tag
            $tag = substr($cipherTextWithTag, -$tagLength);
            $cipherText = substr($cipherTextWithTag, 0, -$tagLength);

            // Derive key (first 32 bytes of secret)
            $key = str_pad(substr($secretKey, 0, 32), 32, "\0");

            $decrypted = openssl_decrypt(
                $cipherText,
                'aes-256-gcm',
                $key,
                OPENSSL_RAW_DATA,
                $iv,
                $tag
            );

            if ($decrypted === false) {
                Log::warning('SMS Payment: Decryption failed');
                return null;
            }

            $payload = json_decode($decrypted, true);
            if (json_last_error() !== JSON_ERROR_NONE) {
                Log::warning('SMS Payment: Invalid JSON in payload');
                return null;
            }

            return $payload;
        } catch (\Exception $e) {
            Log::error('SMS Payment: Decryption error', ['error' => $e->getMessage()]);
            return null;
        }
    }

    /**
     * Verify HMAC signature.
     */
    public function verifySignature(string $data, string $signature, string $secretKey): bool
    {
        $expected = base64_encode(hash_hmac('sha256', $data, $secretKey, true));
        return hash_equals($expected, $signature);
    }

    /**
     * Generate a unique payment amount for a transaction.
     */
    public function generateUniqueAmount(
        float $baseAmount,
        ?int $transactionId = null,
        string $transactionType = 'order',
        int $expiryMinutes = 30
    ): ?UniquePaymentAmount {
        return UniquePaymentAmount::generate(
            $baseAmount,
            $transactionId,
            $transactionType,
            $expiryMinutes
        );
    }

    /**
     * Get pending (unmatched) notifications.
     */
    public function getPendingNotifications(int $limit = 50)
    {
        return SmsPaymentNotification::where('status', 'pending')
            ->orderBy('created_at', 'desc')
            ->limit($limit)
            ->get();
    }

    /**
     * Cleanup expired data.
     */
    public function cleanup(): void
    {
        // Expire old unique amounts
        UniquePaymentAmount::where('status', 'reserved')
            ->where('expires_at', '<=', now())
            ->update(['status' => 'expired']);

        // Clean old nonces (older than 24 hours)
        DB::table('sms_payment_nonces')
            ->where('used_at', '<', now()->subDay())
            ->delete();

        // Expire old pending notifications (older than 7 days)
        SmsPaymentNotification::where('status', 'pending')
            ->where('created_at', '<', now()->subDays(7))
            ->update(['status' => 'expired']);
    }
}
