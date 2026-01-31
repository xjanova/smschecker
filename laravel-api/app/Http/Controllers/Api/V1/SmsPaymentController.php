<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\OrderApproval;
use App\Models\SmsCheckerDevice;
use App\Models\SmsPaymentNotification;
use App\Models\UniquePaymentAmount;
use App\Services\SmsPaymentService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Validator;

class SmsPaymentController extends Controller
{
    public function __construct(
        private SmsPaymentService $smsPaymentService
    ) {}

    /**
     * Receive an SMS payment notification from the Android app.
     *
     * POST /api/v1/sms-payment/notify
     */
    public function notify(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        // Validate required headers
        $signature = $request->header('X-Signature');
        $nonce = $request->header('X-Nonce');
        $timestamp = $request->header('X-Timestamp');

        if (!$signature || !$nonce || !$timestamp) {
            return response()->json([
                'success' => false,
                'message' => 'Missing required security headers',
            ], 400);
        }

        // Check timestamp freshness (within 5 minutes)
        $requestTime = intval($timestamp);
        $currentTime = intval(round(microtime(true) * 1000));
        if (abs($currentTime - $requestTime) > 300000) {
            return response()->json([
                'success' => false,
                'message' => 'Request timestamp expired',
            ], 400);
        }

        // Get encrypted data
        $encryptedData = $request->input('data');
        if (!$encryptedData) {
            return response()->json([
                'success' => false,
                'message' => 'No payload data',
            ], 400);
        }

        // Verify HMAC signature
        $signatureData = $encryptedData . $nonce . $timestamp;
        if (!$this->smsPaymentService->verifySignature($signatureData, $signature, $device->secret_key)) {
            Log::warning('SMS Payment: Invalid signature', [
                'device_id' => $device->device_id,
                'ip' => $request->ip(),
            ]);
            return response()->json([
                'success' => false,
                'message' => 'Invalid signature',
            ], 401);
        }

        // Decrypt payload
        $payload = $this->smsPaymentService->decryptPayload($encryptedData, $device->secret_key);
        if (!$payload) {
            return response()->json([
                'success' => false,
                'message' => 'Failed to decrypt payload',
            ], 400);
        }

        // Validate payload fields
        $validator = Validator::make($payload, [
            'bank' => 'required|string|max:20',
            'type' => 'required|in:credit,debit',
            'amount' => 'required|numeric|min:0.01',
            'account_number' => 'nullable|string|max:50',
            'sender_or_receiver' => 'nullable|string|max:255',
            'reference_number' => 'nullable|string|max:100',
            'sms_timestamp' => 'required|numeric',
            'device_id' => 'required|string',
            'nonce' => 'required|string|max:50',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'success' => false,
                'message' => 'Invalid payload data',
                'errors' => $validator->errors(),
            ], 422);
        }

        // Process the notification
        $result = $this->smsPaymentService->processNotification(
            $payload,
            $device,
            $request->ip()
        );

        return response()->json($result, $result['success'] ? 200 : 400);
    }

    /**
     * Check device status and pending count.
     *
     * GET /api/v1/sms-payment/status
     */
    public function status(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $pendingCount = SmsPaymentNotification::where('device_id', $device->device_id)
            ->where('status', 'pending')
            ->count();

        return response()->json([
            'success' => true,
            'status' => $device->status,
            'pending_count' => $pendingCount,
            'message' => null,
        ]);
    }

    /**
     * Register a new device.
     *
     * POST /api/v1/sms-payment/register-device
     */
    public function registerDevice(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $validator = Validator::make($request->all(), [
            'device_id' => 'required|string|max:50',
            'device_name' => 'required|string|max:100',
            'platform' => 'required|string|max:20',
            'app_version' => 'required|string|max:20',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'success' => false,
                'message' => 'Validation failed',
                'errors' => $validator->errors(),
            ], 422);
        }

        $device->update([
            'device_name' => $request->input('device_name'),
            'platform' => $request->input('platform'),
            'app_version' => $request->input('app_version'),
            'last_active_at' => now(),
            'ip_address' => $request->ip(),
        ]);

        return response()->json([
            'success' => true,
            'message' => 'Device registered successfully',
        ]);
    }

    /**
     * Generate a unique payment amount for checkout.
     * Called by the web checkout process, NOT by the Android app.
     *
     * POST /api/v1/sms-payment/generate-amount
     */
    public function generateAmount(Request $request): JsonResponse
    {
        $validator = Validator::make($request->all(), [
            'base_amount' => 'required|numeric|min:1',
            'transaction_id' => 'nullable|integer',
            'transaction_type' => 'nullable|string|max:50',
            'expiry_minutes' => 'nullable|integer|min:5|max:60',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'success' => false,
                'message' => 'Validation failed',
                'errors' => $validator->errors(),
            ], 422);
        }

        $uniqueAmount = $this->smsPaymentService->generateUniqueAmount(
            $request->input('base_amount'),
            $request->input('transaction_id'),
            $request->input('transaction_type', 'order'),
            $request->input('expiry_minutes', 30)
        );

        if (!$uniqueAmount) {
            return response()->json([
                'success' => false,
                'message' => 'Unable to generate unique amount. Too many pending transactions at this price.',
            ], 409);
        }

        return response()->json([
            'success' => true,
            'message' => 'Unique amount generated',
            'data' => [
                'base_amount' => number_format((float)$uniqueAmount->base_amount, 2, '.', ''),
                'unique_amount' => number_format((float)$uniqueAmount->unique_amount, 2, '.', ''),
                'expires_at' => $uniqueAmount->expires_at->toIso8601String(),
                'display_amount' => '฿' . number_format((float)$uniqueAmount->unique_amount, 2),
            ],
        ]);
    }

    /**
     * List orders with filters.
     * GET /api/v1/sms-payment/orders
     */
    public function orders(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $query = OrderApproval::forDevice($device->device_id)
            ->with('notification')
            ->orderBy('created_at', 'desc');

        if ($request->has('status')) {
            $query->where('approval_status', $request->input('status'));
        }
        if ($request->has('date_from')) {
            $query->where('created_at', '>=', $request->input('date_from'));
        }
        if ($request->has('date_to')) {
            $query->where('created_at', '<=', $request->input('date_to'));
        }

        $orders = $query->paginate($request->input('per_page', 20));

        return response()->json([
            'success' => true,
            'data' => $orders,
        ]);
    }

    /**
     * Approve a single order.
     * POST /api/v1/sms-payment/orders/{id}/approve
     */
    public function approveOrder(Request $request, int $id): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $approval = OrderApproval::forDevice($device->device_id)->findOrFail($id);
        $approval->approve('device');

        return response()->json([
            'success' => true,
            'message' => 'Order approved',
            'data' => $approval->fresh(),
        ]);
    }

    /**
     * Reject a single order.
     * POST /api/v1/sms-payment/orders/{id}/reject
     */
    public function rejectOrder(Request $request, int $id): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $approval = OrderApproval::forDevice($device->device_id)->findOrFail($id);
        $approval->reject(
            $request->input('reason', ''),
            'device'
        );

        return response()->json([
            'success' => true,
            'message' => 'Order rejected',
            'data' => $approval->fresh(),
        ]);
    }

    /**
     * Cancel a single order.
     * POST /api/v1/sms-payment/orders/{id}/cancel
     */
    public function cancelOrder(Request $request, int $id): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $approval = OrderApproval::forDevice($device->device_id)->findOrFail($id);
        $approval->cancel(
            $request->input('reason', ''),
            'device'
        );

        return response()->json([
            'success' => true,
            'message' => 'Order cancelled',
            'data' => $approval->fresh(),
        ]);
    }

    /**
     * Delete a single order (soft-delete via status for sync).
     * DELETE /api/v1/sms-payment/orders/{id}
     */
    public function deleteOrder(Request $request, int $id): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $approval = OrderApproval::forDevice($device->device_id)->findOrFail($id);
        $approval->markDeleted('device');

        return response()->json([
            'success' => true,
            'message' => 'Order deleted',
        ]);
    }

    /**
     * Update order details (amount, product info).
     * PUT /api/v1/sms-payment/orders/{id}
     */
    public function updateOrder(Request $request, int $id): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $approval = OrderApproval::forDevice($device->device_id)->findOrFail($id);

        $details = $request->only([
            'amount', 'order_number', 'product_name',
            'product_details', 'quantity', 'customer_name',
        ]);

        $approval->updateDetails($details, 'device');

        return response()->json([
            'success' => true,
            'message' => 'Order updated',
            'data' => $approval->fresh(),
        ]);
    }

    /**
     * Bulk approve orders by IDs.
     * POST /api/v1/sms-payment/orders/bulk-approve
     */
    public function bulkApproveOrders(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $ids = $request->input('ids', []);
        $approved = 0;

        foreach ($ids as $id) {
            $approval = OrderApproval::forDevice($device->device_id)
                ->pendingReview()
                ->find($id);

            if ($approval) {
                $approval->approve('device');
                $approved++;
            }
        }

        return response()->json([
            'success' => true,
            'message' => "$approved orders approved",
            'approved_count' => $approved,
        ]);
    }

    /**
     * Bi-directional sync: returns orders updated since a version.
     * GET /api/v1/sms-payment/orders/sync
     */
    public function syncOrders(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $sinceVersion = (int) $request->input('since_version', 0);

        $orders = OrderApproval::forDevice($device->device_id)
            ->where('synced_version', '>', $sinceVersion)
            ->with('notification')
            ->orderBy('synced_version')
            ->limit(100)
            ->get();

        return response()->json([
            'success' => true,
            'data' => [
                'orders' => $orders,
                'latest_version' => $orders->max('synced_version') ?? $sinceVersion,
            ],
        ]);
    }

    /**
     * Get device settings (approval mode).
     * GET /api/v1/sms-payment/device-settings
     */
    public function deviceSettings(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        return response()->json([
            'success' => true,
            'data' => [
                'approval_mode' => $device->approval_mode ?? config('smschecker.default_approval_mode', 'auto'),
            ],
        ]);
    }

    /**
     * Update device settings (approval mode).
     * PUT /api/v1/sms-payment/device-settings
     */
    public function updateDeviceSettings(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $validator = Validator::make($request->all(), [
            'approval_mode' => 'required|in:auto,manual,smart',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'success' => false,
                'message' => 'Validation failed',
                'errors' => $validator->errors(),
            ], 422);
        }

        $device->update(['approval_mode' => $request->input('approval_mode')]);

        return response()->json([
            'success' => true,
            'message' => 'Settings updated',
            'data' => [
                'approval_mode' => $device->approval_mode,
            ],
        ]);
    }

    /**
     * Dashboard stats with daily breakdown.
     * GET /api/v1/sms-payment/dashboard-stats
     */
    public function dashboardStats(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $days = (int) $request->input('days', 7);
        $since = now()->subDays($days);
        $deviceId = $device->device_id;

        $stats = [
            'total_orders' => OrderApproval::forDevice($deviceId)->where('created_at', '>=', $since)->count(),
            'auto_approved' => OrderApproval::forDevice($deviceId)->where('created_at', '>=', $since)->where('approval_status', 'auto_approved')->count(),
            'manually_approved' => OrderApproval::forDevice($deviceId)->where('created_at', '>=', $since)->where('approval_status', 'manually_approved')->count(),
            'pending_review' => OrderApproval::forDevice($deviceId)->where('created_at', '>=', $since)->where('approval_status', 'pending_review')->count(),
            'rejected' => OrderApproval::forDevice($deviceId)->where('created_at', '>=', $since)->where('approval_status', 'rejected')->count(),
            'total_amount' => 0.0,
            'daily_breakdown' => [],
        ];

        // Total amount from matched notifications
        $notificationIds = OrderApproval::forDevice($deviceId)
            ->where('created_at', '>=', $since)
            ->approved()
            ->pluck('notification_id');

        $stats['total_amount'] = (float) SmsPaymentNotification::whereIn('id', $notificationIds)->sum('amount');

        // Daily breakdown
        for ($i = $days - 1; $i >= 0; $i--) {
            $date = now()->subDays($i)->toDateString();
            $dayStart = now()->subDays($i)->startOfDay();
            $dayEnd = now()->subDays($i)->endOfDay();

            $dayApprovals = OrderApproval::forDevice($deviceId)
                ->whereBetween('created_at', [$dayStart, $dayEnd]);

            $dayNotificationIds = (clone $dayApprovals)->approved()->pluck('notification_id');
            $dayAmount = (float) SmsPaymentNotification::whereIn('id', $dayNotificationIds)->sum('amount');

            $stats['daily_breakdown'][] = [
                'date' => $date,
                'count' => (clone $dayApprovals)->count(),
                'approved' => (clone $dayApprovals)->approved()->count(),
                'rejected' => (clone $dayApprovals)->where('approval_status', 'rejected')->count(),
                'amount' => $dayAmount,
            ];
        }

        return response()->json([
            'success' => true,
            'data' => $stats,
        ]);
    }

    /**
     * Get notification history for admin dashboard.
     *
     * GET /api/v1/sms-payment/notifications
     */
    public function notifications(Request $request): JsonResponse
    {
        $query = SmsPaymentNotification::orderBy('created_at', 'desc');

        if ($request->has('status')) {
            $query->where('status', $request->input('status'));
        }

        if ($request->has('bank')) {
            $query->where('bank', $request->input('bank'));
        }

        if ($request->has('type')) {
            $query->where('type', $request->input('type'));
        }

        $notifications = $query->paginate($request->input('per_page', 20));

        return response()->json([
            'success' => true,
            'data' => $notifications,
        ]);
    }
}
