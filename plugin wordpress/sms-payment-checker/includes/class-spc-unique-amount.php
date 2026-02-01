<?php
/**
 * Unique payment amount generation and matching
 *
 * Adds a small decimal suffix (0.01-0.99) to base amounts to create
 * unique payment amounts that can be matched against incoming SMS notifications.
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

class SPC_Unique_Amount {

    /**
     * Generate a unique amount for a base price
     *
     * @param float       $base_amount      Original order amount
     * @param string|null $transaction_id    External transaction ID
     * @param string|null $transaction_type  Transaction type label
     * @param int|null    $order_id          WooCommerce order ID
     * @param int         $expiry_minutes    Minutes until expiry (default 30)
     * @return array|WP_Error Unique amount data or error
     */
    public static function generate( $base_amount, $transaction_id = null, $transaction_type = null, $order_id = null, $expiry_minutes = 30 ) {
        global $wpdb;
        $tables   = SPC_Database::get_table_names();
        $settings = get_option( 'spc_settings', array() );
        $max_pending = isset( $settings['max_pending_per_amount'] ) ? (int) $settings['max_pending_per_amount'] : 99;

        $base = round( (float) $base_amount, 2 );
        $base_floor = floor( $base ); // Integer part

        // Expire old reservations first
        $wpdb->query( $wpdb->prepare(
            "UPDATE {$tables['amounts']} SET status = 'expired' WHERE status = 'reserved' AND expires_at < %s",
            current_time( 'mysql' )
        ) );

        // Find used suffixes for this base amount
        $used_suffixes = $wpdb->get_col( $wpdb->prepare(
            "SELECT decimal_suffix FROM {$tables['amounts']} WHERE base_amount = %f AND status = 'reserved'",
            $base_floor
        ) );

        // Find an available suffix (1-99)
        $suffix = null;
        for ( $i = 1; $i <= $max_pending; $i++ ) {
            if ( ! in_array( $i, $used_suffixes ) ) {
                $suffix = $i;
                break;
            }
        }

        if ( $suffix === null ) {
            return new WP_Error(
                'no_suffix_available',
                __( 'All unique amounts are currently reserved for this price. Please try again later.', 'sms-payment-checker' ),
                array( 'status' => 503 )
            );
        }

        $unique_amount = $base_floor + ( $suffix / 100 );
        $expires_at    = gmdate( 'Y-m-d H:i:s', time() + ( $expiry_minutes * 60 ) );

        $wpdb->insert(
            $tables['amounts'],
            array(
                'base_amount'      => $base_floor,
                'unique_amount'    => $unique_amount,
                'decimal_suffix'   => $suffix,
                'transaction_id'   => $transaction_id,
                'transaction_type' => $transaction_type,
                'order_id'         => $order_id,
                'status'           => 'reserved',
                'expires_at'       => $expires_at,
            ),
            array( '%f', '%f', '%d', '%s', '%s', '%d', '%s', '%s' )
        );

        return array(
            'id'             => $wpdb->insert_id,
            'base_amount'    => $base_floor,
            'unique_amount'  => $unique_amount,
            'decimal_suffix' => $suffix,
            'expires_at'     => $expires_at,
            'display_amount' => sprintf( '฿%s', number_format( $unique_amount, 2 ) ),
        );
    }

    /**
     * Try to match an incoming notification amount
     *
     * @param float  $amount    Incoming payment amount
     * @param string $bank      Bank code
     * @param string $reference Reference number (optional fallback matching)
     * @return object|null Matched unique_amount record or null
     */
    public static function match( $amount, $bank = '', $reference = '' ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        // Primary match: exact unique amount
        $match = $wpdb->get_row( $wpdb->prepare(
            "SELECT * FROM {$tables['amounts']}
             WHERE unique_amount = %f AND status = 'reserved' AND expires_at > %s
             ORDER BY created_at ASC LIMIT 1",
            round( (float) $amount, 2 ),
            current_time( 'mysql' )
        ) );

        if ( $match ) {
            // Mark as used
            $wpdb->update(
                $tables['amounts'],
                array(
                    'status'     => 'used',
                    'matched_at' => current_time( 'mysql' ),
                ),
                array( 'id' => $match->id ),
                array( '%s', '%s' ),
                array( '%d' )
            );
            return $match;
        }

        return null;
    }

    /**
     * Cancel a unique amount reservation
     *
     * @param int $id Unique amount ID
     * @return bool
     */
    public static function cancel( $id ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        return $wpdb->update(
            $tables['amounts'],
            array( 'status' => 'cancelled' ),
            array( 'id' => $id, 'status' => 'reserved' ),
            array( '%s' ),
            array( '%d', '%s' )
        ) !== false;
    }

    /**
     * Expire stale reservations
     */
    public static function expire_stale() {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $wpdb->query( $wpdb->prepare(
            "UPDATE {$tables['amounts']} SET status = 'expired' WHERE status = 'reserved' AND expires_at < %s",
            current_time( 'mysql' )
        ) );
    }
}
