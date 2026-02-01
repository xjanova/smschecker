<?php
/**
 * Database management class
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

class SPC_Database {

    /**
     * Database version
     */
    const DB_VERSION = '1.0.0';

    /**
     * Get table names
     */
    public static function get_table_names() {
        global $wpdb;
        return array(
            'devices'       => $wpdb->prefix . 'spc_devices',
            'notifications' => $wpdb->prefix . 'spc_notifications',
            'amounts'       => $wpdb->prefix . 'spc_unique_amounts',
            'nonces'        => $wpdb->prefix . 'spc_nonces',
            'orders'        => $wpdb->prefix . 'spc_order_approvals',
        );
    }

    /**
     * Plugin activation — create tables
     */
    public static function activate() {
        global $wpdb;
        $charset_collate = $wpdb->get_charset_collate();
        $tables = self::get_table_names();

        require_once ABSPATH . 'wp-admin/includes/upgrade.php';

        // ─── Devices table ───
        $sql = "CREATE TABLE {$tables['devices']} (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            device_id varchar(100) NOT NULL,
            device_name varchar(255) NOT NULL DEFAULT '',
            api_key varchar(64) NOT NULL,
            secret_key varchar(64) NOT NULL,
            platform varchar(20) NOT NULL DEFAULT 'android',
            app_version varchar(20) DEFAULT NULL,
            status varchar(20) NOT NULL DEFAULT 'active',
            last_active_at datetime DEFAULT NULL,
            user_id bigint(20) unsigned DEFAULT NULL,
            ip_address varchar(45) DEFAULT NULL,
            created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            UNIQUE KEY device_id (device_id),
            UNIQUE KEY api_key (api_key)
        ) $charset_collate;";
        dbDelta( $sql );

        // ─── Notifications table ───
        $sql = "CREATE TABLE {$tables['notifications']} (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            bank varchar(20) NOT NULL,
            type varchar(10) NOT NULL,
            amount decimal(15,2) NOT NULL,
            account_number varchar(50) DEFAULT '',
            sender_or_receiver varchar(255) DEFAULT '',
            reference_number varchar(100) DEFAULT '',
            sms_timestamp bigint(20) DEFAULT NULL,
            device_id varchar(100) DEFAULT NULL,
            nonce varchar(50) DEFAULT NULL,
            status varchar(20) NOT NULL DEFAULT 'pending',
            matched_transaction_id varchar(100) DEFAULT NULL,
            matched_order_id bigint(20) unsigned DEFAULT NULL,
            raw_payload text DEFAULT NULL,
            ip_address varchar(45) DEFAULT NULL,
            created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            KEY amount_status (amount, status),
            KEY bank_type (bank, type),
            KEY reference_number (reference_number),
            KEY device_id (device_id),
            KEY matched_order_id (matched_order_id)
        ) $charset_collate;";
        dbDelta( $sql );

        // ─── Unique amounts table ───
        $sql = "CREATE TABLE {$tables['amounts']} (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            base_amount decimal(15,2) NOT NULL,
            unique_amount decimal(15,2) NOT NULL,
            decimal_suffix smallint NOT NULL,
            transaction_id varchar(100) DEFAULT NULL,
            transaction_type varchar(50) DEFAULT NULL,
            order_id bigint(20) unsigned DEFAULT NULL,
            status varchar(20) NOT NULL DEFAULT 'reserved',
            expires_at datetime NOT NULL,
            matched_at datetime DEFAULT NULL,
            created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            KEY amount_suffix_status (base_amount, decimal_suffix, status),
            KEY unique_amount_status (unique_amount, status),
            KEY order_id (order_id),
            KEY expires_at (expires_at)
        ) $charset_collate;";
        dbDelta( $sql );

        // ─── Nonces table (replay protection) ───
        $sql = "CREATE TABLE {$tables['nonces']} (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            nonce varchar(50) NOT NULL,
            device_id varchar(100) NOT NULL,
            used_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            UNIQUE KEY nonce_device (nonce, device_id)
        ) $charset_collate;";
        dbDelta( $sql );

        // ─── Order approvals table ───
        $sql = "CREATE TABLE {$tables['orders']} (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            notification_id bigint(20) unsigned DEFAULT NULL,
            order_id bigint(20) unsigned DEFAULT NULL,
            matched_transaction_id varchar(100) DEFAULT NULL,
            device_id varchar(100) DEFAULT NULL,
            approval_status varchar(30) NOT NULL DEFAULT 'pending_review',
            confidence varchar(20) NOT NULL DEFAULT 'high',
            approved_by varchar(100) DEFAULT NULL,
            approved_at datetime DEFAULT NULL,
            rejected_at datetime DEFAULT NULL,
            rejection_reason varchar(500) DEFAULT NULL,
            order_details_json text DEFAULT NULL,
            synced_version bigint(20) NOT NULL DEFAULT 0,
            created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            KEY notification_id (notification_id),
            KEY order_id (order_id),
            KEY approval_status (approval_status)
        ) $charset_collate;";
        dbDelta( $sql );

        update_option( 'spc_db_version', self::DB_VERSION );

        // Set default options
        if ( false === get_option( 'spc_settings' ) ) {
            update_option( 'spc_settings', array(
                'timestamp_tolerance'     => 300,
                'unique_amount_expiry'    => 30,
                'max_pending_per_amount'  => 99,
                'rate_limit_per_minute'   => 30,
                'nonce_expiry_hours'      => 24,
                'auto_confirm_matched'    => true,
                'notify_on_match'         => true,
                'approval_mode'           => 'auto',
                'supported_banks'         => array( 'KBANK', 'SCB', 'KTB', 'BBL', 'GSB', 'BAY', 'TTB', 'PROMPTPAY', 'CIMB', 'KKP', 'LH', 'TISCO', 'UOB', 'ICBC', 'BAAC' ),
                'woocommerce_integration' => true,
            ) );
        }
    }
}
