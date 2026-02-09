<?php

namespace App\Services;

use App\Models\RemoteOrderApproval;
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
                self::log('warning', 'SMS Payment: Duplicate nonce detected', [
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
            if ($notification->type === 'credit') {
                $matched = $notification->attemptMatch();
            }

            // Create a RemoteOrderApproval so the SmsChecker app can see this bill
            // and track its approval status
            $approvalStatus = $matched ? 'auto_approved' : 'pending_review';
            $orderDetails = [
                'order_number' => $notification->matched_transaction_id
                    ? 'TXN-' . $notification->matched_transaction_id
                    : 'SMS-' . $notification->id,
                'product_name' => 'SMS Payment - ' . $notification->bank,
                'amount' => (float) $notification->amount,
                'customer_name' => $notification->sender_or_receiver,
            ];

            $approval = RemoteOrderApproval::create([
                'notification_id' => $notification->id,
                'matched_transaction_id' => $notification->matched_transaction_id,
                'device_id' => $device->device_id,
                'approval_status' => $approvalStatus,
                'confidence' => 'high',
                'approved_by' => $matched ? 'auto' : null,
                'approved_at' => $matched ? now() : null,
                'order_details_json' => $orderDetails,
                'server_name' => config('app.name', 'Server'),
                'synced_version' => intval(round(microtime(true) * 1000)),
            ]);

            self::log('info', 'SMS Payment notification processed', [
                'notification_id' => $notification->id,
                'approval_id' => $approval->id,
                'bank' => $notification->bank,
                'type' => $notification->type,
                'amount' => $notification->amount,
                'matched' => $matched,
            ]);

            return [
                'success' => true,
                'message' => $matched ? 'Payment matched and confirmed' : 'Notification recorded',
                'data' => [
                    'notification_id' => $notification->id,
                    'approval_id' => $approval->id,
                    'status' => $notification->status,
                    'matched' => $matched,
                    'matched_transaction_id' => $notification->matched_transaction_id,
                ],
            ];
        });
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
                self::log('warning', 'SMS Payment: Decryption failed');
                return null;
            }

            $payload = json_decode($decrypted, true);
            if (json_last_error() !== JSON_ERROR_NONE) {
                self::log('warning', 'SMS Payment: Invalid JSON in payload');
                return null;
            }

            return $payload;
        } catch (\Exception $e) {
            self::log('error', 'SMS Payment: Decryption error', ['error' => $e->getMessage()]);
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

    /**
     * Safe logging helper that works both inside and outside Laravel app context.
     * Prevents facade errors during unit testing.
     */
    private static function log(string $level, string $message, array $context = []): void
    {
        try {
            Log::$level($message, $context);
        } catch (\RuntimeException $e) {
            // Facade root not set (running outside Laravel app context)
            // Silently ignore - this happens during unit testing
        }
    }
}
