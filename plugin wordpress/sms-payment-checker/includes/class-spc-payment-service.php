<?php
/**
 * Payment processing service
 *
 * Handles incoming SMS notifications, decryption, verification,
 * auto-matching with WooCommerce orders, and order approval workflows.
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

class SPC_Payment_Service {

    /**
     * Process an incoming payment notification from the Android app
     *
     * @param object $device    Authenticated device object
     * @param string $encrypted Encrypted payload (base64)
     * @param string $signature HMAC signature
     * @param string $nonce     Request nonce
     * @param string $timestamp Request timestamp
     * @return array|WP_Error Result array or error
     */
    public static function process_notification( $device, $encrypted, $signature, $nonce, $timestamp ) {
        global $wpdb;
        $tables   = SPC_Database::get_table_names();
        $settings = get_option( 'spc_settings', array() );

        // 1. Verify timestamp (±5 minutes)
        $tolerance = isset( $settings['timestamp_tolerance'] ) ? (int) $settings['timestamp_tolerance'] : 300;
        $ts_diff   = abs( time() * 1000 - (int) $timestamp ); // Timestamp is in milliseconds
        if ( $ts_diff > $tolerance * 1000 ) {
            return new WP_Error( 'timestamp_expired', 'Request timestamp is too old or in the future.', array( 'status' => 400 ) );
        }

        // 2. Verify signature: HMAC(encrypted_data + nonce + timestamp)
        $signature_data = $encrypted . $nonce . $timestamp;
        if ( ! SPC_Crypto::verify_signature( $signature_data, $signature, $device->secret_key ) ) {
            return new WP_Error( 'invalid_signature', 'Signature verification failed.', array( 'status' => 401 ) );
        }

        // 3. Check nonce (replay protection)
        if ( ! SPC_Crypto::verify_nonce( $nonce, $device->device_id ) ) {
            return new WP_Error( 'nonce_replayed', 'This nonce has already been used.', array( 'status' => 409 ) );
        }

        // 4. Decrypt payload
        $decrypted = SPC_Crypto::decrypt( $encrypted, $device->secret_key );
        if ( $decrypted === false ) {
            return new WP_Error( 'decryption_failed', 'Failed to decrypt payload.', array( 'status' => 400 ) );
        }

        $payload = json_decode( $decrypted, true );
        if ( ! $payload || ! isset( $payload['bank'], $payload['amount'], $payload['type'] ) ) {
            return new WP_Error( 'invalid_payload', 'Invalid notification payload.', array( 'status' => 400 ) );
        }

        // 5. Store notification
        $notification_data = array(
            'bank'               => sanitize_text_field( $payload['bank'] ),
            'type'               => sanitize_text_field( $payload['type'] ),
            'amount'             => (float) $payload['amount'],
            'account_number'     => sanitize_text_field( $payload['account_number'] ?? '' ),
            'sender_or_receiver' => sanitize_text_field( $payload['sender_or_receiver'] ?? '' ),
            'reference_number'   => sanitize_text_field( $payload['reference_number'] ?? '' ),
            'sms_timestamp'      => (int) ( $payload['sms_timestamp'] ?? 0 ),
            'device_id'          => $device->device_id,
            'nonce'              => $nonce,
            'status'             => 'pending',
            'raw_payload'        => $decrypted,
            'ip_address'         => self::get_client_ip(),
        );

        $wpdb->insert( $tables['notifications'], $notification_data, array(
            '%s', '%s', '%f', '%s', '%s', '%s', '%d', '%s', '%s', '%s', '%s', '%s',
        ) );
        $notification_id = $wpdb->insert_id;

        // 6. Try auto-match
        $match_result = self::try_auto_match( $notification_id, $notification_data );

        return array(
            'success'         => true,
            'message'         => 'Notification received.',
            'notification_id' => $notification_id,
            'matched'         => $match_result['matched'],
            'match_details'   => $match_result['details'] ?? null,
        );
    }

    /**
     * Attempt to auto-match a notification with a pending payment
     *
     * @param int   $notification_id
     * @param array $data Notification data
     * @return array Match result
     */
    private static function try_auto_match( $notification_id, $data ) {
        global $wpdb;
        $tables   = SPC_Database::get_table_names();
        $settings = get_option( 'spc_settings', array() );

        // Only match credit (incoming) transactions
        if ( $data['type'] !== 'credit' ) {
            return array( 'matched' => false );
        }

        // 1. Try unique amount matching
        $amount_match = SPC_Unique_Amount::match( $data['amount'], $data['bank'], $data['reference_number'] );

        if ( $amount_match ) {
            $order_id       = $amount_match->order_id;
            $transaction_id = $amount_match->transaction_id;

            // Update notification status
            $wpdb->update(
                $tables['notifications'],
                array(
                    'status'                 => 'matched',
                    'matched_transaction_id' => $transaction_id,
                    'matched_order_id'       => $order_id,
                ),
                array( 'id' => $notification_id ),
                array( '%s', '%s', '%d' ),
                array( '%d' )
            );

            // Create order approval record
            $approval_status = 'auto_approved';
            if ( isset( $settings['approval_mode'] ) && $settings['approval_mode'] === 'manual' ) {
                $approval_status = 'pending_review';
            }

            $wpdb->insert(
                $tables['orders'],
                array(
                    'notification_id'        => $notification_id,
                    'order_id'               => $order_id,
                    'matched_transaction_id' => $transaction_id,
                    'device_id'              => $data['device_id'],
                    'approval_status'        => $approval_status,
                    'confidence'             => 'high',
                    'approved_by'            => $approval_status === 'auto_approved' ? 'system' : null,
                    'approved_at'            => $approval_status === 'auto_approved' ? current_time( 'mysql' ) : null,
                ),
                array( '%d', '%d', '%s', '%s', '%s', '%s', '%s', '%s' )
            );

            // WooCommerce integration: update order status
            if ( $order_id && $approval_status === 'auto_approved' && class_exists( 'WC_Order' ) ) {
                $wc_integration = $settings['woocommerce_integration'] ?? true;
                if ( $wc_integration ) {
                    self::update_woocommerce_order( $order_id, $data );
                }
            }

            // Fire action hook for other plugins
            do_action( 'spc_payment_matched', $notification_id, $order_id, $data );

            return array(
                'matched' => true,
                'details' => array(
                    'order_id'        => $order_id,
                    'transaction_id'  => $transaction_id,
                    'approval_status' => $approval_status,
                ),
            );
        }

        // 2. Fallback: try WooCommerce order matching by amount
        if ( class_exists( 'WC_Order' ) ) {
            $wc_match = self::try_woocommerce_match( $data['amount'], $data['reference_number'] );
            if ( $wc_match ) {
                $wpdb->update(
                    $tables['notifications'],
                    array(
                        'status'            => 'matched',
                        'matched_order_id'  => $wc_match,
                    ),
                    array( 'id' => $notification_id ),
                    array( '%s', '%d' ),
                    array( '%d' )
                );

                $approval_status = isset( $settings['approval_mode'] ) && $settings['approval_mode'] === 'manual'
                    ? 'pending_review'
                    : 'auto_approved';

                $wpdb->insert(
                    $tables['orders'],
                    array(
                        'notification_id' => $notification_id,
                        'order_id'        => $wc_match,
                        'device_id'       => $data['device_id'],
                        'approval_status' => $approval_status,
                        'confidence'      => 'medium',
                        'approved_by'     => $approval_status === 'auto_approved' ? 'system' : null,
                        'approved_at'     => $approval_status === 'auto_approved' ? current_time( 'mysql' ) : null,
                    ),
                    array( '%d', '%d', '%s', '%s', '%s', '%s', '%s' )
                );

                if ( $approval_status === 'auto_approved' ) {
                    self::update_woocommerce_order( $wc_match, $data );
                }

                do_action( 'spc_payment_matched', $notification_id, $wc_match, $data );

                return array(
                    'matched' => true,
                    'details' => array(
                        'order_id'        => $wc_match,
                        'approval_status' => $approval_status,
                        'confidence'      => 'medium',
                    ),
                );
            }
        }

        return array( 'matched' => false );
    }

    /**
     * Try to match amount against pending WooCommerce orders
     *
     * @param float  $amount
     * @param string $reference
     * @return int|null WooCommerce order ID or null
     */
    private static function try_woocommerce_match( $amount, $reference = '' ) {
        if ( ! class_exists( 'wc_get_orders' ) && ! function_exists( 'wc_get_orders' ) ) {
            return null;
        }

        $orders = wc_get_orders( array(
            'status'     => array( 'pending', 'on-hold' ),
            'limit'      => 10,
            'orderby'    => 'date',
            'order'      => 'DESC',
            'meta_query' => array(), // No custom meta filter
        ) );

        foreach ( $orders as $order ) {
            $order_total = (float) $order->get_total();
            if ( abs( $order_total - (float) $amount ) < 0.01 ) {
                return $order->get_id();
            }
        }

        return null;
    }

    /**
     * Update WooCommerce order status on payment match
     *
     * @param int   $order_id WooCommerce order ID
     * @param array $data     Notification data
     */
    private static function update_woocommerce_order( $order_id, $data ) {
        if ( ! class_exists( 'WC_Order' ) ) {
            return;
        }

        $order = wc_get_order( $order_id );
        if ( ! $order ) {
            return;
        }

        $order->payment_complete();
        $order->add_order_note( sprintf(
            /* translators: %1$s: bank name, %2$s: amount, %3$s: reference */
            __( 'SMS Payment verified: %1$s %2$s (Ref: %3$s)', 'sms-payment-checker' ),
            $data['bank'],
            number_format( (float) $data['amount'], 2 ),
            $data['reference_number'] ?: '-'
        ) );

        do_action( 'spc_woocommerce_order_completed', $order_id, $data );
    }

    /**
     * Get orders with approval status
     *
     * @param array $args Query arguments
     * @return array
     */
    public static function get_order_approvals( $args = array() ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $defaults = array(
            'status'   => null,
            'page'     => 1,
            'per_page' => 20,
        );
        $args = wp_parse_args( $args, $defaults );

        $where  = '1=1';
        $params = array();

        if ( $args['status'] ) {
            $where .= ' AND o.approval_status = %s';
            $params[] = $args['status'];
        }

        $offset = ( $args['page'] - 1 ) * $args['per_page'];

        $query = "SELECT o.*, n.bank, n.type, n.amount, n.sms_timestamp, n.sender_or_receiver
                  FROM {$tables['orders']} o
                  LEFT JOIN {$tables['notifications']} n ON o.notification_id = n.id
                  WHERE $where
                  ORDER BY o.created_at DESC
                  LIMIT %d OFFSET %d";

        $params[] = $args['per_page'];
        $params[] = $offset;

        $results = $wpdb->get_results( $wpdb->prepare( $query, $params ) );

        // Total count
        $count_query = "SELECT COUNT(*) FROM {$tables['orders']} o WHERE $where";
        if ( $args['status'] ) {
            $total = $wpdb->get_var( $wpdb->prepare( $count_query, $args['status'] ) );
        } else {
            $total = $wpdb->get_var( $count_query );
        }

        return array(
            'data'         => $results,
            'current_page' => $args['page'],
            'last_page'    => ceil( (int) $total / $args['per_page'] ),
            'total'        => (int) $total,
        );
    }

    /**
     * Approve an order
     *
     * @param int $approval_id Order approval ID
     * @return bool
     */
    public static function approve_order( $approval_id ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $approval = $wpdb->get_row( $wpdb->prepare(
            "SELECT * FROM {$tables['orders']} WHERE id = %d",
            $approval_id
        ) );

        if ( ! $approval || $approval->approval_status !== 'pending_review' ) {
            return false;
        }

        $wpdb->update(
            $tables['orders'],
            array(
                'approval_status' => 'manually_approved',
                'approved_by'     => wp_get_current_user()->user_login ?? 'device',
                'approved_at'     => current_time( 'mysql' ),
            ),
            array( 'id' => $approval_id ),
            array( '%s', '%s', '%s' ),
            array( '%d' )
        );

        // If linked to WooCommerce order, complete it
        if ( $approval->order_id && class_exists( 'WC_Order' ) ) {
            $notification = $wpdb->get_row( $wpdb->prepare(
                "SELECT * FROM {$tables['notifications']} WHERE id = %d",
                $approval->notification_id
            ) );
            if ( $notification ) {
                self::update_woocommerce_order( $approval->order_id, (array) $notification );
            }
        }

        do_action( 'spc_order_approved', $approval_id );
        return true;
    }

    /**
     * Reject an order
     *
     * @param int    $approval_id Order approval ID
     * @param string $reason      Rejection reason
     * @return bool
     */
    public static function reject_order( $approval_id, $reason = '' ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $wpdb->update(
            $tables['orders'],
            array(
                'approval_status'  => 'rejected',
                'rejected_at'      => current_time( 'mysql' ),
                'rejection_reason' => sanitize_text_field( $reason ),
            ),
            array( 'id' => $approval_id ),
            array( '%s', '%s', '%s' ),
            array( '%d' )
        );

        do_action( 'spc_order_rejected', $approval_id, $reason );
        return true;
    }

    /**
     * Get dashboard statistics
     *
     * @param int $days Number of days to include
     * @return array
     */
    public static function get_dashboard_stats( $days = 7 ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $stats = array(
            'total_orders'       => 0,
            'auto_approved'      => 0,
            'manually_approved'  => 0,
            'pending_review'     => 0,
            'rejected'           => 0,
            'total_amount'       => 0.0,
            'daily_breakdown'    => array(),
        );

        // Counts by status
        $rows = $wpdb->get_results(
            "SELECT approval_status, COUNT(*) as cnt FROM {$tables['orders']} GROUP BY approval_status"
        );
        foreach ( $rows as $row ) {
            $stats['total_orders'] += (int) $row->cnt;
            switch ( $row->approval_status ) {
                case 'auto_approved':
                    $stats['auto_approved'] = (int) $row->cnt;
                    break;
                case 'manually_approved':
                    $stats['manually_approved'] = (int) $row->cnt;
                    break;
                case 'pending_review':
                    $stats['pending_review'] = (int) $row->cnt;
                    break;
                case 'rejected':
                    $stats['rejected'] = (int) $row->cnt;
                    break;
            }
        }

        // Total amount
        $stats['total_amount'] = (float) $wpdb->get_var(
            "SELECT COALESCE(SUM(n.amount), 0)
             FROM {$tables['orders']} o
             JOIN {$tables['notifications']} n ON o.notification_id = n.id
             WHERE o.approval_status IN ('auto_approved', 'manually_approved')"
        );

        // Daily breakdown
        $since = gmdate( 'Y-m-d', strtotime( "-{$days} days" ) );
        $daily = $wpdb->get_results( $wpdb->prepare(
            "SELECT DATE(o.created_at) as date,
                    COUNT(*) as count,
                    SUM(CASE WHEN o.approval_status IN ('auto_approved','manually_approved') THEN 1 ELSE 0 END) as approved,
                    SUM(CASE WHEN o.approval_status = 'rejected' THEN 1 ELSE 0 END) as rejected,
                    COALESCE(SUM(n.amount), 0) as amount
             FROM {$tables['orders']} o
             LEFT JOIN {$tables['notifications']} n ON o.notification_id = n.id
             WHERE DATE(o.created_at) >= %s
             GROUP BY DATE(o.created_at)
             ORDER BY date ASC",
            $since
        ) );

        foreach ( $daily as $d ) {
            $stats['daily_breakdown'][] = array(
                'date'     => $d->date,
                'count'    => (int) $d->count,
                'approved' => (int) $d->approved,
                'rejected' => (int) $d->rejected,
                'amount'   => (float) $d->amount,
            );
        }

        return $stats;
    }

    /**
     * Cleanup old data
     */
    public static function cleanup() {
        global $wpdb;
        $tables   = SPC_Database::get_table_names();
        $settings = get_option( 'spc_settings', array() );

        // Expire stale unique amounts
        SPC_Unique_Amount::expire_stale();

        // Clean old nonces (>24h)
        $nonce_hours = isset( $settings['nonce_expiry_hours'] ) ? (int) $settings['nonce_expiry_hours'] : 24;
        $wpdb->query( $wpdb->prepare(
            "DELETE FROM {$tables['nonces']} WHERE used_at < %s",
            gmdate( 'Y-m-d H:i:s', strtotime( "-{$nonce_hours} hours" ) )
        ) );

        // Clean old notifications (>30 days, only unmatched)
        $wpdb->query(
            "DELETE FROM {$tables['notifications']}
             WHERE status = 'pending' AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY)"
        );
    }

    /**
     * Get client IP
     */
    private static function get_client_ip() {
        $ip_keys = array( 'HTTP_X_FORWARDED_FOR', 'HTTP_CLIENT_IP', 'REMOTE_ADDR' );
        foreach ( $ip_keys as $key ) {
            if ( ! empty( $_SERVER[ $key ] ) ) {
                $ip = explode( ',', sanitize_text_field( wp_unslash( $_SERVER[ $key ] ) ) );
                return trim( $ip[0] );
            }
        }
        return '127.0.0.1';
    }
}
