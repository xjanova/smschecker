<?php
/**
 * Device management (CRUD, authentication, QR generation)
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

class SPC_Device_Manager {

    /**
     * Create a new device
     *
     * @param string $device_name Device name
     * @param int    $user_id     WordPress user ID (optional)
     * @return array Device data including api_key and secret_key
     */
    public static function create_device( $device_name, $user_id = null ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $device_id  = wp_generate_uuid4();
        $api_key    = SPC_Crypto::generate_api_key();
        $secret_key = SPC_Crypto::generate_api_key();

        $wpdb->insert(
            $tables['devices'],
            array(
                'device_id'   => $device_id,
                'device_name' => sanitize_text_field( $device_name ),
                'api_key'     => $api_key,
                'secret_key'  => $secret_key,
                'platform'    => 'android',
                'status'      => 'active',
                'user_id'     => $user_id,
            ),
            array( '%s', '%s', '%s', '%s', '%s', '%s', '%d' )
        );

        return array(
            'id'         => $wpdb->insert_id,
            'device_id'  => $device_id,
            'device_name' => $device_name,
            'api_key'    => $api_key,
            'secret_key' => $secret_key,
        );
    }

    /**
     * Authenticate device by API key
     *
     * @param string $api_key API key from header
     * @return object|null Device object or null
     */
    public static function authenticate( $api_key ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $device = $wpdb->get_row( $wpdb->prepare(
            "SELECT * FROM {$tables['devices']} WHERE api_key = %s AND status = 'active'",
            $api_key
        ) );

        if ( $device ) {
            // Update last active
            $wpdb->update(
                $tables['devices'],
                array(
                    'last_active_at' => current_time( 'mysql' ),
                    'ip_address'     => self::get_client_ip(),
                ),
                array( 'id' => $device->id ),
                array( '%s', '%s' ),
                array( '%d' )
            );
        }

        return $device;
    }

    /**
     * Get device by ID
     *
     * @param int $id Device database ID
     * @return object|null
     */
    public static function get_device( $id ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        return $wpdb->get_row( $wpdb->prepare(
            "SELECT * FROM {$tables['devices']} WHERE id = %d",
            $id
        ) );
    }

    /**
     * Get all devices
     *
     * @return array
     */
    public static function get_all_devices() {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        return $wpdb->get_results( "SELECT * FROM {$tables['devices']} ORDER BY created_at DESC" );
    }

    /**
     * Update device info (from app registration)
     *
     * @param string $device_id  Device UUID
     * @param array  $data       Data to update
     * @return bool
     */
    public static function update_device_info( $device_id, $data ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $update = array();
        $format = array();

        if ( isset( $data['device_name'] ) ) {
            $update['device_name'] = sanitize_text_field( $data['device_name'] );
            $format[] = '%s';
        }
        if ( isset( $data['platform'] ) ) {
            $update['platform'] = sanitize_text_field( $data['platform'] );
            $format[] = '%s';
        }
        if ( isset( $data['app_version'] ) ) {
            $update['app_version'] = sanitize_text_field( $data['app_version'] );
            $format[] = '%s';
        }

        if ( empty( $update ) ) {
            return false;
        }

        return $wpdb->update(
            $tables['devices'],
            $update,
            array( 'device_id' => $device_id ),
            $format,
            array( '%s' )
        ) !== false;
    }

    /**
     * Delete a device
     *
     * @param int $id Device database ID
     * @return bool
     */
    public static function delete_device( $id ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        return $wpdb->delete(
            $tables['devices'],
            array( 'id' => $id ),
            array( '%d' )
        ) !== false;
    }

    /**
     * Toggle device status
     *
     * @param int    $id     Device ID
     * @param string $status New status (active/inactive/blocked)
     * @return bool
     */
    public static function set_status( $id, $status ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        $valid = array( 'active', 'inactive', 'blocked' );
        if ( ! in_array( $status, $valid, true ) ) {
            return false;
        }

        return $wpdb->update(
            $tables['devices'],
            array( 'status' => $status ),
            array( 'id' => $id ),
            array( '%s' ),
            array( '%d' )
        ) !== false;
    }

    /**
     * Generate QR code data for a device
     *
     * @param int $device_id Database ID
     * @return array|false QR payload or false
     */
    public static function get_qr_data( $device_id ) {
        $device = self::get_device( $device_id );
        if ( ! $device ) {
            return false;
        }

        return array(
            'type'       => 'sms_checker_config',
            'version'    => 2,
            'url'        => rest_url( 'sms-payment/v1' ),
            'apiKey'     => $device->api_key,
            'secretKey'  => $device->secret_key,
            'deviceName' => $device->device_name,
            'deviceId'   => $device->device_id,
        );
    }

    /**
     * Get client IP address
     *
     * @return string
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
