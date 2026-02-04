<?php
/**
 * WordPress Admin Panel for SMS Payment Checker
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

class SPC_Admin {

    /**
     * Initialize admin hooks
     */
    public static function init() {
        add_action( 'admin_menu', array( __CLASS__, 'add_admin_menu' ) );
        add_action( 'admin_enqueue_scripts', array( __CLASS__, 'enqueue_assets' ) );
        add_action( 'wp_ajax_spc_create_device', array( __CLASS__, 'ajax_create_device' ) );
        add_action( 'wp_ajax_spc_delete_device', array( __CLASS__, 'ajax_delete_device' ) );
        add_action( 'wp_ajax_spc_toggle_device', array( __CLASS__, 'ajax_toggle_device' ) );
        add_action( 'wp_ajax_spc_save_settings', array( __CLASS__, 'ajax_save_settings' ) );
        add_action( 'wp_ajax_spc_approve_order', array( __CLASS__, 'ajax_approve_order' ) );
        add_action( 'wp_ajax_spc_reject_order', array( __CLASS__, 'ajax_reject_order' ) );
    }

    /**
     * Add admin menu pages
     */
    public static function add_admin_menu() {
        add_menu_page(
            __( 'SMS Payment', 'sms-payment-checker' ),
            __( 'SMS Payment', 'sms-payment-checker' ),
            'manage_options',
            'spc-dashboard',
            array( __CLASS__, 'render_dashboard' ),
            'dashicons-smartphone',
            56
        );

        add_submenu_page(
            'spc-dashboard',
            __( 'Dashboard', 'sms-payment-checker' ),
            __( 'Dashboard', 'sms-payment-checker' ),
            'manage_options',
            'spc-dashboard',
            array( __CLASS__, 'render_dashboard' )
        );

        add_submenu_page(
            'spc-dashboard',
            __( 'Devices', 'sms-payment-checker' ),
            __( 'Devices', 'sms-payment-checker' ),
            'manage_options',
            'spc-devices',
            array( __CLASS__, 'render_devices' )
        );

        add_submenu_page(
            'spc-dashboard',
            __( 'Order Approvals', 'sms-payment-checker' ),
            __( 'Order Approvals', 'sms-payment-checker' ),
            'manage_options',
            'spc-orders',
            array( __CLASS__, 'render_orders' )
        );

        add_submenu_page(
            'spc-dashboard',
            __( 'Notifications', 'sms-payment-checker' ),
            __( 'Notifications', 'sms-payment-checker' ),
            'manage_options',
            'spc-notifications',
            array( __CLASS__, 'render_notifications' )
        );

        add_submenu_page(
            'spc-dashboard',
            __( 'Settings', 'sms-payment-checker' ),
            __( 'Settings', 'sms-payment-checker' ),
            'manage_options',
            'spc-settings',
            array( __CLASS__, 'render_settings' )
        );
    }

    /**
     * Enqueue admin assets
     */
    public static function enqueue_assets( $hook ) {
        if ( strpos( $hook, 'spc-' ) === false ) {
            return;
        }

        wp_enqueue_style( 'spc-admin', SPC_PLUGIN_URL . 'assets/css/admin.css', array(), SPC_VERSION );
        wp_enqueue_script( 'spc-admin', SPC_PLUGIN_URL . 'assets/js/admin.js', array( 'jquery' ), SPC_VERSION, true );
        wp_localize_script( 'spc-admin', 'spcAdmin', array(
            'ajax_url' => admin_url( 'admin-ajax.php' ),
            'nonce'    => wp_create_nonce( 'spc_admin_nonce' ),
            'rest_url' => rest_url( 'sms-payment/v1/' ),
            'strings'  => array(
                'confirm_delete' => __( 'Are you sure you want to delete this device?', 'sms-payment-checker' ),
                'confirm_approve' => __( 'Approve this order?', 'sms-payment-checker' ),
                'confirm_reject' => __( 'Reject this order?', 'sms-payment-checker' ),
                'saved'          => __( 'Settings saved.', 'sms-payment-checker' ),
                'error'          => __( 'An error occurred.', 'sms-payment-checker' ),
            ),
        ) );

        // QR Code library (Browser-compatible)
        wp_enqueue_script( 'qrcode-js', 'https://cdn.jsdelivr.net/npm/qrcodejs@1.0.0/qrcode.min.js', array(), '1.0.0', true );
    }

    // ═══════════════════════════════════════
    // PAGE RENDERERS
    // ═══════════════════════════════════════

    /**
     * Dashboard page
     */
    public static function render_dashboard() {
        $stats = SPC_Payment_Service::get_dashboard_stats();
        include SPC_PLUGIN_DIR . 'admin/views/dashboard.php';
    }

    /**
     * Devices page
     */
    public static function render_devices() {
        $devices = SPC_Device_Manager::get_all_devices();
        include SPC_PLUGIN_DIR . 'admin/views/devices.php';
    }

    /**
     * Orders page
     */
    public static function render_orders() {
        $page   = isset( $_GET['paged'] ) ? max( 1, (int) $_GET['paged'] ) : 1;
        $status = isset( $_GET['status'] ) ? sanitize_text_field( $_GET['status'] ) : null;
        $orders = SPC_Payment_Service::get_order_approvals( array(
            'status'   => $status,
            'page'     => $page,
            'per_page' => 20,
        ) );
        include SPC_PLUGIN_DIR . 'admin/views/orders.php';
    }

    /**
     * Notifications page
     */
    public static function render_notifications() {
        global $wpdb;
        $tables   = SPC_Database::get_table_names();
        $page     = isset( $_GET['paged'] ) ? max( 1, (int) $_GET['paged'] ) : 1;
        $per_page = 20;
        $offset   = ( $page - 1 ) * $per_page;

        $notifications = $wpdb->get_results( $wpdb->prepare(
            "SELECT * FROM {$tables['notifications']} ORDER BY created_at DESC LIMIT %d OFFSET %d",
            $per_page,
            $offset
        ) );
        $total = (int) $wpdb->get_var( "SELECT COUNT(*) FROM {$tables['notifications']}" );

        include SPC_PLUGIN_DIR . 'admin/views/notifications.php';
    }

    /**
     * Settings page
     */
    public static function render_settings() {
        $settings = get_option( 'spc_settings', array() );
        include SPC_PLUGIN_DIR . 'admin/views/settings.php';
    }

    // ═══════════════════════════════════════
    // AJAX HANDLERS
    // ═══════════════════════════════════════

    /**
     * Create device via AJAX
     */
    public static function ajax_create_device() {
        check_ajax_referer( 'spc_admin_nonce', 'nonce' );

        if ( ! current_user_can( 'manage_options' ) ) {
            wp_send_json_error( 'Unauthorized' );
        }

        $name = sanitize_text_field( $_POST['device_name'] ?? '' );
        if ( empty( $name ) ) {
            wp_send_json_error( 'Device name is required.' );
        }

        $device = SPC_Device_Manager::create_device( $name, get_current_user_id() );
        wp_send_json_success( $device );
    }

    /**
     * Delete device via AJAX
     */
    public static function ajax_delete_device() {
        check_ajax_referer( 'spc_admin_nonce', 'nonce' );

        if ( ! current_user_can( 'manage_options' ) ) {
            wp_send_json_error( 'Unauthorized' );
        }

        $id = (int) ( $_POST['device_id'] ?? 0 );
        SPC_Device_Manager::delete_device( $id );
        wp_send_json_success();
    }

    /**
     * Toggle device status via AJAX
     */
    public static function ajax_toggle_device() {
        check_ajax_referer( 'spc_admin_nonce', 'nonce' );

        if ( ! current_user_can( 'manage_options' ) ) {
            wp_send_json_error( 'Unauthorized' );
        }

        $id     = (int) ( $_POST['device_id'] ?? 0 );
        $status = sanitize_text_field( $_POST['status'] ?? 'inactive' );
        SPC_Device_Manager::set_status( $id, $status );
        wp_send_json_success();
    }

    /**
     * Save settings via AJAX
     */
    public static function ajax_save_settings() {
        check_ajax_referer( 'spc_admin_nonce', 'nonce' );

        if ( ! current_user_can( 'manage_options' ) ) {
            wp_send_json_error( 'Unauthorized' );
        }

        $settings = get_option( 'spc_settings', array() );

        // Update settings from POST
        $fields = array(
            'timestamp_tolerance',
            'unique_amount_expiry',
            'max_pending_per_amount',
            'rate_limit_per_minute',
            'nonce_expiry_hours',
        );
        foreach ( $fields as $field ) {
            if ( isset( $_POST[ $field ] ) ) {
                $settings[ $field ] = (int) $_POST[ $field ];
            }
        }

        $bool_fields = array( 'auto_confirm_matched', 'notify_on_match', 'woocommerce_integration' );
        foreach ( $bool_fields as $field ) {
            $settings[ $field ] = isset( $_POST[ $field ] ) && $_POST[ $field ] === '1';
        }

        if ( isset( $_POST['approval_mode'] ) ) {
            $settings['approval_mode'] = sanitize_text_field( $_POST['approval_mode'] );
        }

        update_option( 'spc_settings', $settings );
        wp_send_json_success( 'Settings saved.' );
    }

    /**
     * Approve order via AJAX
     */
    public static function ajax_approve_order() {
        check_ajax_referer( 'spc_admin_nonce', 'nonce' );

        if ( ! current_user_can( 'manage_options' ) ) {
            wp_send_json_error( 'Unauthorized' );
        }

        $id     = (int) ( $_POST['order_id'] ?? 0 );
        $result = SPC_Payment_Service::approve_order( $id );
        wp_send_json_success( $result );
    }

    /**
     * Reject order via AJAX
     */
    public static function ajax_reject_order() {
        check_ajax_referer( 'spc_admin_nonce', 'nonce' );

        if ( ! current_user_can( 'manage_options' ) ) {
            wp_send_json_error( 'Unauthorized' );
        }

        $id     = (int) ( $_POST['order_id'] ?? 0 );
        $reason = sanitize_text_field( $_POST['reason'] ?? '' );
        $result = SPC_Payment_Service::reject_order( $id, $reason );
        wp_send_json_success( $result );
    }
}
