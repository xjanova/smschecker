<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\RemoteOrderApproval;
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

    // =====================================================================
    // Order Approval Endpoints (required by SmsChecker Android app)
    // =====================================================================

    /**
     * Get paginated list of orders for the device.
     *
     * GET /api/v1/sms-payment/orders
     */
    public function getOrders(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $query = RemoteOrderApproval::with('notification')
            ->orderBy('created_at', 'desc');

        // Filter by status
        $status = $request->input('status');
        if ($status && $status !== 'all') {
            $query->where('approval_status', $status);
        } else {
            // Exclude deleted by default
            $query->where('approval_status', '!=', 'deleted');
        }

        // Filter by date range
        if ($request->has('date_from')) {
            $query->where('created_at', '>=', $request->input('date_from'));
        }
        if ($request->has('date_to')) {
            $query->where('created_at', '<=', $request->input('date_to'));
        }

        $perPage = min((int) $request->input('per_page', 20), 100);
        $paginated = $query->paginate($perPage);

        // Transform to API format
        $items = collect($paginated->items())->map(fn($order) => $order->toApiResponse());

        return response()->json([
            'success' => true,
            'data' => [
                'data' => $items,
                'current_page' => $paginated->currentPage(),
                'last_page' => $paginated->lastPage(),
                'total' => $paginated->total(),
            ],
        ]);
    }

    /**
     * Approve a single order by ID or bill_reference.
     *
     * POST /api/v1/sms-payment/orders/{id}/approve
     */
    public function approveOrder(Request $request, string $id): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $order = RemoteOrderApproval::findByIdentifier($id);
        if (!$order) {
            return response()->json([
                'success' => false,
                'message' => 'Order not found',
            ], 404);
        }

        // Already approved → return 422 so app knows it's done
        if (in_array($order->approval_status, ['auto_approved', 'manually_approved'])) {
            return response()->json([
                'success' => false,
                'message' => 'Order already approved',
            ], 422);
        }

        $approved = $order->approve('device');
        if (!$approved) {
            return response()->json([
                'success' => false,
                'message' => 'Order cannot be approved (status: ' . $order->approval_status . ')',
            ], 422);
        }

        return response()->json([
            'success' => true,
            'message' => 'Order approved successfully',
        ]);
    }

    /**
     * Reject a single order.
     *
     * POST /api/v1/sms-payment/orders/{id}/reject
     */
    public function rejectOrder(Request $request, string $id): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $order = RemoteOrderApproval::findByIdentifier($id);
        if (!$order) {
            return response()->json([
                'success' => false,
                'message' => 'Order not found',
            ], 404);
        }

        if (in_array($order->approval_status, ['rejected'])) {
            return response()->json([
                'success' => false,
                'message' => 'Order already rejected',
            ], 422);
        }

        $reason = $request->input('reason', '');
        $rejected = $order->reject($reason);
        if (!$rejected) {
            return response()->json([
                'success' => false,
                'message' => 'Order cannot be rejected (status: ' . $order->approval_status . ')',
            ], 422);
        }

        return response()->json([
            'success' => true,
            'message' => 'Order rejected',
        ]);
    }

    /**
     * Bulk approve multiple orders.
     *
     * POST /api/v1/sms-payment/orders/bulk-approve
     */
    public function bulkApproveOrders(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $ids = $request->input('ids', []);
        if (empty($ids)) {
            return response()->json([
                'success' => false,
                'message' => 'No order IDs provided',
            ], 400);
        }

        $approved = 0;
        $failed = 0;
        foreach ($ids as $id) {
            $order = RemoteOrderApproval::findByIdentifier((string) $id);
            if ($order && $order->approve('device')) {
                $approved++;
            } else {
                $failed++;
            }
        }

        return response()->json([
            'success' => true,
            'message' => "Approved $approved orders" . ($failed > 0 ? ", $failed failed" : ''),
            'data' => [
                'approved' => $approved,
                'failed' => $failed,
            ],
        ]);
    }

    /**
     * Sync orders since a version (delta sync).
     *
     * GET /api/v1/sms-payment/orders/sync
     */
    public function syncOrders(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $sinceVersion = (int) $request->input('since_version', 0);

        $orders = RemoteOrderApproval::with('notification')
            ->where('synced_version', '>', $sinceVersion)
            ->orderBy('synced_version', 'asc')
            ->limit(100)
            ->get();

        $latestVersion = $orders->isNotEmpty()
            ? $orders->max('synced_version')
            : $sinceVersion;

        return response()->json([
            'success' => true,
            'data' => [
                'orders' => $orders->map(fn($order) => $order->toApiResponse()),
                'latest_version' => $latestVersion,
            ],
        ]);
    }

    /**
     * Match order by SMS amount (match-only mode).
     *
     * GET /api/v1/sms-payment/orders/match
     */
    public function matchOrder(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $amount = $request->input('amount');
        if (!$amount || !is_numeric($amount)) {
            return response()->json([
                'success' => true,
                'data' => [
                    'matched' => false,
                    'order' => null,
                    'message' => 'Invalid amount',
                ],
            ]);
        }

        $amountFloat = (float) $amount;

        // Try to match via unique_payment_amounts first
        $uniqueAmount = UniquePaymentAmount::where('unique_amount', $amountFloat)
            ->where('status', 'reserved')
            ->where('expires_at', '>', now())
            ->first();

        if ($uniqueAmount) {
            // Found a match via unique amount - find or create the RemoteOrderApproval
            $order = RemoteOrderApproval::where('matched_transaction_id', $uniqueAmount->transaction_id)
                ->first();

            if (!$order) {
                // Create a new RemoteOrderApproval for this match
                $order = RemoteOrderApproval::createFromOrder([
                    'order_number' => $uniqueAmount->transaction_type . '-' . $uniqueAmount->transaction_id,
                    'product_name' => $uniqueAmount->transaction_type === 'wallet_topup' ? 'เติมเงิน Wallet' : 'Order Payment',
                    'amount' => $amountFloat,
                ], $device->device_id);
                $order->matched_transaction_id = $uniqueAmount->transaction_id;
                $order->save();
            }

            $order->load('notification');

            return response()->json([
                'success' => true,
                'data' => [
                    'matched' => true,
                    'order' => $order->toApiResponse(),
                    'message' => 'Order matched by unique amount',
                ],
            ]);
        }

        // Fallback: look for pending RemoteOrderApproval with matching amount
        $order = RemoteOrderApproval::with('notification')
            ->where('approval_status', 'pending_review')
            ->whereJsonContains('order_details_json->amount', $amountFloat)
            ->first();

        // Also try matching as string since JSON can store as string
        if (!$order) {
            $amountStr = number_format($amountFloat, 2, '.', '');
            $order = RemoteOrderApproval::with('notification')
                ->where('approval_status', 'pending_review')
                ->whereRaw("JSON_EXTRACT(order_details_json, '$.amount') = ?", [$amountStr])
                ->first();
        }

        if ($order) {
            return response()->json([
                'success' => true,
                'data' => [
                    'matched' => true,
                    'order' => $order->toApiResponse(),
                    'message' => 'Order matched by amount',
                ],
            ]);
        }

        return response()->json([
            'success' => true,
            'data' => [
                'matched' => false,
                'order' => null,
                'message' => 'No matching order found',
            ],
        ]);
    }

    /**
     * Get device settings.
     *
     * GET /api/v1/sms-payment/device-settings
     */
    public function getDeviceSettings(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        return response()->json([
            'success' => true,
            'data' => [
                'approval_mode' => $device->approval_mode ?? 'auto',
            ],
        ]);
    }

    /**
     * Update device settings.
     *
     * PUT /api/v1/sms-payment/device-settings
     */
    public function updateDeviceSettings(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $mode = $request->input('approval_mode', 'auto');
        if (!in_array($mode, ['auto', 'manual', 'smart'])) {
            return response()->json([
                'success' => false,
                'message' => 'Invalid approval mode',
            ], 422);
        }

        $device->update(['approval_mode' => $mode]);

        return response()->json([
            'success' => true,
            'message' => 'Settings updated',
            'data' => [
                'approval_mode' => $mode,
            ],
        ]);
    }

    /**
     * Get dashboard statistics.
     *
     * GET /api/v1/sms-payment/dashboard-stats
     */
    public function getDashboardStats(Request $request): JsonResponse
    {
        $device = $request->attributes->get('sms_checker_device');
        if (!$device instanceof SmsCheckerDevice) {
            return response()->json(['success' => false, 'message' => 'Unauthorized'], 401);
        }

        $days = (int) $request->input('days', 7);
        $since = now()->subDays($days);

        $total = RemoteOrderApproval::where('created_at', '>=', $since)->count();
        $autoApproved = RemoteOrderApproval::where('created_at', '>=', $since)
            ->where('approval_status', 'auto_approved')->count();
        $manuallyApproved = RemoteOrderApproval::where('created_at', '>=', $since)
            ->where('approval_status', 'manually_approved')->count();
        $pending = RemoteOrderApproval::where('created_at', '>=', $since)
            ->where('approval_status', 'pending_review')->count();
        $rejected = RemoteOrderApproval::where('created_at', '>=', $since)
            ->where('approval_status', 'rejected')->count();

        // Total amount from approved orders
        $totalAmount = RemoteOrderApproval::where('created_at', '>=', $since)
            ->whereIn('approval_status', ['auto_approved', 'manually_approved'])
            ->get()
            ->sum(function ($order) {
                return (float) ($order->order_details_json['amount'] ?? 0);
            });

        // Daily breakdown
        $dailyBreakdown = [];
        for ($i = $days - 1; $i >= 0; $i--) {
            $date = now()->subDays($i)->format('Y-m-d');
            $dayStart = now()->subDays($i)->startOfDay();
            $dayEnd = now()->subDays($i)->endOfDay();

            $dayOrders = RemoteOrderApproval::whereBetween('created_at', [$dayStart, $dayEnd]);

            $dailyBreakdown[] = [
                'date' => $date,
                'count' => (clone $dayOrders)->count(),
                'approved' => (clone $dayOrders)->whereIn('approval_status', ['auto_approved', 'manually_approved'])->count(),
                'rejected' => (clone $dayOrders)->where('approval_status', 'rejected')->count(),
                'amount' => (clone $dayOrders)->whereIn('approval_status', ['auto_approved', 'manually_approved'])->get()->sum(function ($o) {
                    return (float) ($o->order_details_json['amount'] ?? 0);
                }),
            ];
        }

        return response()->json([
            'success' => true,
            'data' => [
                'total_orders' => $total,
                'auto_approved' => $autoApproved,
                'manually_approved' => $manuallyApproved,
                'pending_review' => $pending,
                'rejected' => $rejected,
                'total_amount' => $totalAmount,
                'daily_breakdown' => $dailyBreakdown,
            ],
        ]);
    }
}
