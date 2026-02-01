<?php
/**
 * REST API Controller
 *
 * Provides API endpoints compatible with the Android SMS Payment Checker app.
 * All endpoints match the Laravel plugin API structure for drop-in compatibility.
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

class SPC_REST_Controller {

    const NAMESPACE = 'sms-payment/v1';

    /**
     * Register all REST routes
     */
    public static function register_routes() {
        // ─── Device-authenticated endpoints ───

        register_rest_route( self::NAMESPACE, '/notify', array(
            'methods'             => 'POST',
            'callback'            => array( __CLASS__, 'handle_notify' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/status', array(
            'methods'             => 'GET',
            'callback'            => array( __CLASS__, 'handle_status' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/register-device', array(
            'methods'             => 'POST',
            'callback'            => array( __CLASS__, 'handle_register_device' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/orders', array(
            'methods'             => 'GET',
            'callback'            => array( __CLASS__, 'handle_get_orders' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/orders/(?P<id>\d+)/approve', array(
            'methods'             => 'POST',
            'callback'            => array( __CLASS__, 'handle_approve_order' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/orders/(?P<id>\d+)/reject', array(
            'methods'             => 'POST',
            'callback'            => array( __CLASS__, 'handle_reject_order' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/orders/bulk-approve', array(
            'methods'             => 'POST',
            'callback'            => array( __CLASS__, 'handle_bulk_approve' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/orders/sync', array(
            'methods'             => 'GET',
            'callback'            => array( __CLASS__, 'handle_sync_orders' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        register_rest_route( self::NAMESPACE, '/device-settings', array(
            array(
                'methods'             => 'GET',
                'callback'            => array( __CLASS__, 'handle_get_settings' ),
                'permission_callback' => array( __CLASS__, 'verify_device' ),
            ),
            array(
                'methods'             => 'PUT',
                'callback'            => array( __CLASS__, 'handle_update_settings' ),
                'permission_callback' => array( __CLASS__, 'verify_device' ),
            ),
        ) );

        register_rest_route( self::NAMESPACE, '/dashboard-stats', array(
            'methods'             => 'GET',
            'callback'            => array( __CLASS__, 'handle_dashboard_stats' ),
            'permission_callback' => array( __CLASS__, 'verify_device' ),
        ) );

        // ─── Admin / public endpoints ───

        register_rest_route( self::NAMESPACE, '/generate-amount', array(
            'methods'             => 'POST',
            'callback'            => array( __CLASS__, 'handle_generate_amount' ),
            'permission_callback' => array( __CLASS__, 'verify_admin_or_nonce' ),
        ) );

        register_rest_route( self::NAMESPACE, '/notifications', array(
            'methods'             => 'GET',
            'callback'            => array( __CLASS__, 'handle_get_notifications' ),
            'permission_callback' => array( __CLASS__, 'verify_admin_or_nonce' ),
        ) );
    }

    // ═══════════════════════════════════════
    // PERMISSION CALLBACKS
    // ═══════════════════════════════════════

    /**
     * Verify device by API key header
     */
    public static function verify_device( $request ) {
        $api_key = $request->get_header( 'X-Api-Key' );
        if ( empty( $api_key ) ) {
            return new WP_Error( 'missing_api_key', 'X-Api-Key header is required.', array( 'status' => 401 ) );
        }

        $device = SPC_Device_Manager::authenticate( $api_key );
        if ( ! $device ) {
            return new WP_Error( 'invalid_api_key', 'Invalid or inactive device API key.', array( 'status' => 401 ) );
        }

        // Store device in request for use by callbacks
        $request->set_param( '_spc_device', $device );
        return true;
    }

    /**
     * Verify admin user or WP nonce
     */
    public static function verify_admin_or_nonce( $request ) {
        // Allow admin users
        if ( current_user_can( 'manage_options' ) ) {
            return true;
        }

        // Allow WP REST nonce
        $nonce = $request->get_header( 'X-WP-Nonce' );
        if ( $nonce && wp_verify_nonce( $nonce, 'wp_rest' ) ) {
            return true;
        }

        // Allow API key auth too
        $api_key = $request->get_header( 'X-Api-Key' );
        if ( $api_key ) {
            $device = SPC_Device_Manager::authenticate( $api_key );
            if ( $device ) {
                return true;
            }
        }

        return new WP_Error( 'unauthorized', 'Authentication required.', array( 'status' => 401 ) );
    }

    // ═══════════════════════════════════════
    // DEVICE ENDPOINTS
    // ═══════════════════════════════════════

    /**
     * POST /notify — Receive SMS payment notification
     */
    public static function handle_notify( $request ) {
        $device = $request->get_param( '_spc_device' );
        $body   = $request->get_json_params();

        $encrypted = $body['data'] ?? '';
        $signature = $request->get_header( 'X-Signature' ) ?? '';
        $nonce     = $request->get_header( 'X-Nonce' ) ?? '';
        $timestamp = $request->get_header( 'X-Timestamp' ) ?? '';

        if ( empty( $encrypted ) || empty( $signature ) || empty( $nonce ) || empty( $timestamp ) ) {
            return new WP_Error( 'missing_fields', 'Missing required headers or body.', array( 'status' => 400 ) );
        }

        $result = SPC_Payment_Service::process_notification( $device, $encrypted, $signature, $nonce, $timestamp );

        if ( is_wp_error( $result ) ) {
            return $result;
        }

        return rest_ensure_response( $result );
    }

    /**
     * GET /status — Check device status
     */
    public static function handle_status( $request ) {
        global $wpdb;
        $device = $request->get_param( '_spc_device' );
        $tables = SPC_Database::get_table_names();

        $pending_count = (int) $wpdb->get_var( $wpdb->prepare(
            "SELECT COUNT(*) FROM {$tables['notifications']} WHERE device_id = %s AND status = 'pending'",
            $device->device_id
        ) );

        return rest_ensure_response( array(
            'success'       => true,
            'status'        => $device->status,
            'pending_count' => $pending_count,
        ) );
    }

    /**
     * POST /register-device — Register or update device info
     */
    public static function handle_register_device( $request ) {
        $device = $request->get_param( '_spc_device' );
        $body   = $request->get_json_params();

        SPC_Device_Manager::update_device_info( $device->device_id, array(
            'device_name' => $body['device_name'] ?? null,
            'platform'    => $body['platform'] ?? null,
            'app_version' => $body['app_version'] ?? null,
        ) );

        return rest_ensure_response( array(
            'success' => true,
            'message' => 'Device registered.',
        ) );
    }

    // ═══════════════════════════════════════
    // ORDER APPROVAL ENDPOINTS
    // ═══════════════════════════════════════

    /**
     * GET /orders — List order approvals
     */
    public static function handle_get_orders( $request ) {
        $status   = $request->get_param( 'status' );
        $page     = max( 1, (int) $request->get_param( 'page' ) ?: 1 );
        $per_page = min( 100, max( 1, (int) $request->get_param( 'per_page' ) ?: 20 ) );

        $result = SPC_Payment_Service::get_order_approvals( array(
            'status'   => $status,
            'page'     => $page,
            'per_page' => $per_page,
        ) );

        return rest_ensure_response( array(
            'success' => true,
            'data'    => $result,
        ) );
    }

    /**
     * POST /orders/{id}/approve
     */
    public static function handle_approve_order( $request ) {
        $id     = (int) $request->get_param( 'id' );
        $result = SPC_Payment_Service::approve_order( $id );

        return rest_ensure_response( array(
            'success' => $result,
            'message' => $result ? 'Order approved.' : 'Failed to approve order.',
        ) );
    }

    /**
     * POST /orders/{id}/reject
     */
    public static function handle_reject_order( $request ) {
        $id     = (int) $request->get_param( 'id' );
        $body   = $request->get_json_params();
        $reason = $body['reason'] ?? '';

        $result = SPC_Payment_Service::reject_order( $id, $reason );

        return rest_ensure_response( array(
            'success' => $result,
            'message' => $result ? 'Order rejected.' : 'Failed to reject order.',
        ) );
    }

    /**
     * POST /orders/bulk-approve
     */
    public static function handle_bulk_approve( $request ) {
        $body = $request->get_json_params();
        $ids  = $body['ids'] ?? array();

        $approved = 0;
        foreach ( $ids as $id ) {
            if ( SPC_Payment_Service::approve_order( (int) $id ) ) {
                $approved++;
            }
        }

        return rest_ensure_response( array(
            'success' => true,
            'message' => "{$approved} orders approved.",
        ) );
    }

    /**
     * GET /orders/sync — Get orders since version
     */
    public static function handle_sync_orders( $request ) {
        global $wpdb;
        $tables        = SPC_Database::get_table_names();
        $since_version = max( 0, (int) $request->get_param( 'since_version' ) );

        $orders = $wpdb->get_results( $wpdb->prepare(
            "SELECT o.*, n.bank, n.type, n.amount, n.sms_timestamp, n.sender_or_receiver
             FROM {$tables['orders']} o
             LEFT JOIN {$tables['notifications']} n ON o.notification_id = n.id
             WHERE o.synced_version > %d
             ORDER BY o.synced_version ASC
             LIMIT 100",
            $since_version
        ) );

        $latest_version = 0;
        if ( ! empty( $orders ) ) {
            $latest_version = (int) end( $orders )->synced_version;
        }

        return rest_ensure_response( array(
            'success' => true,
            'data'    => array(
                'orders'         => $orders,
                'latest_version' => $latest_version,
            ),
        ) );
    }

    // ═══════════════════════════════════════
    // SETTINGS ENDPOINTS
    // ═══════════════════════════════════════

    /**
     * GET /device-settings
     */
    public static function handle_get_settings( $request ) {
        $settings = get_option( 'spc_settings', array() );

        return rest_ensure_response( array(
            'success' => true,
            'data'    => array(
                'approval_mode' => $settings['approval_mode'] ?? 'auto',
            ),
        ) );
    }

    /**
     * PUT /device-settings
     */
    public static function handle_update_settings( $request ) {
        $body     = $request->get_json_params();
        $settings = get_option( 'spc_settings', array() );

        if ( isset( $body['approval_mode'] ) ) {
            $valid_modes = array( 'auto', 'manual' );
            if ( in_array( $body['approval_mode'], $valid_modes, true ) ) {
                $settings['approval_mode'] = $body['approval_mode'];
                update_option( 'spc_settings', $settings );
            }
        }

        return rest_ensure_response( array(
            'success' => true,
            'message' => 'Settings updated.',
        ) );
    }

    /**
     * GET /dashboard-stats
     */
    public static function handle_dashboard_stats( $request ) {
        $days = min( 30, max( 1, (int) $request->get_param( 'days' ) ?: 7 ) );

        $stats = SPC_Payment_Service::get_dashboard_stats( $days );

        return rest_ensure_response( array(
            'success' => true,
            'data'    => $stats,
        ) );
    }

    // ═══════════════════════════════════════
    // ADMIN ENDPOINTS
    // ═══════════════════════════════════════

    /**
     * POST /generate-amount — Generate unique payment amount
     */
    public static function handle_generate_amount( $request ) {
        $body = $request->get_json_params();

        if ( ! isset( $body['base_amount'] ) ) {
            return new WP_Error( 'missing_amount', 'base_amount is required.', array( 'status' => 400 ) );
        }

        $expiry = isset( $body['expiry_minutes'] ) ? min( 60, max( 5, (int) $body['expiry_minutes'] ) ) : 30;

        $result = SPC_Unique_Amount::generate(
            (float) $body['base_amount'],
            $body['transaction_id'] ?? null,
            $body['transaction_type'] ?? null,
            $body['order_id'] ?? null,
            $expiry
        );

        if ( is_wp_error( $result ) ) {
            return $result;
        }

        return rest_ensure_response( array(
            'success' => true,
            'data'    => $result,
        ) );
    }

    /**
     * GET /notifications — List notifications
     */
    public static function handle_get_notifications( $request ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $status   = $request->get_param( 'status' );
        $bank     = $request->get_param( 'bank' );
        $per_page = min( 100, max( 1, (int) $request->get_param( 'per_page' ) ?: 20 ) );
        $page     = max( 1, (int) $request->get_param( 'page' ) ?: 1 );

        $where  = '1=1';
        $params = array();

        if ( $status ) {
            $where   .= ' AND status = %s';
            $params[] = $status;
        }
        if ( $bank ) {
            $where   .= ' AND bank = %s';
            $params[] = $bank;
        }

        $offset   = ( $page - 1 ) * $per_page;
        $params[] = $per_page;
        $params[] = $offset;

        $results = $wpdb->get_results( $wpdb->prepare(
            "SELECT * FROM {$tables['notifications']} WHERE $where ORDER BY created_at DESC LIMIT %d OFFSET %d",
            $params
        ) );

        return rest_ensure_response( array(
            'success' => true,
            'data'    => $results,
        ) );
    }
}
