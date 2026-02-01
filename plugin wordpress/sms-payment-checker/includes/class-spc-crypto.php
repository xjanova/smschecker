<?php
/**
 * Cryptographic operations (AES-256-GCM, HMAC, Nonce)
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

class SPC_Crypto {

    /**
     * Decrypt AES-256-GCM data
     *
     * Format: base64( IV(12) + ciphertext + tag(16) )
     *
     * @param string $encrypted_data Base64 encoded encrypted data
     * @param string $secret_key     Secret key string
     * @return string|false Decrypted JSON string or false on failure
     */
    public static function decrypt( $encrypted_data, $secret_key ) {
        $raw = base64_decode( $encrypted_data );
        if ( $raw === false || strlen( $raw ) < 28 ) { // 12 IV + 16 tag minimum
            return false;
        }

        $key = hash( 'sha256', $secret_key, true ); // 32 bytes
        $iv  = substr( $raw, 0, 12 );
        $tag = substr( $raw, -16 );
        $ciphertext = substr( $raw, 12, -16 );

        $decrypted = openssl_decrypt(
            $ciphertext,
            'aes-256-gcm',
            $key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag
        );

        return $decrypted;
    }

    /**
     * Encrypt data with AES-256-GCM
     *
     * @param string $data       JSON string to encrypt
     * @param string $secret_key Secret key string
     * @return string Base64 encoded encrypted data
     */
    public static function encrypt( $data, $secret_key ) {
        $key = hash( 'sha256', $secret_key, true );
        $iv  = random_bytes( 12 );
        $tag = '';

        $encrypted = openssl_encrypt(
            $data,
            'aes-256-gcm',
            $key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            '',
            16
        );

        return base64_encode( $iv . $encrypted . $tag );
    }

    /**
     * Verify HMAC-SHA256 signature
     *
     * @param string $data      Data that was signed
     * @param string $signature Signature to verify
     * @param string $key       Secret key
     * @return bool
     */
    public static function verify_signature( $data, $signature, $key ) {
        $expected = hash_hmac( 'sha256', $data, $key );
        return hash_equals( $expected, $signature );
    }

    /**
     * Generate HMAC-SHA256 signature
     *
     * @param string $data Data to sign
     * @param string $key  Secret key
     * @return string
     */
    public static function generate_signature( $data, $key ) {
        return hash_hmac( 'sha256', $data, $key );
    }

    /**
     * Generate a random API key
     *
     * @param int $length Key length in bytes (output is hex, so double)
     * @return string
     */
    public static function generate_api_key( $length = 32 ) {
        return bin2hex( random_bytes( $length ) );
    }

    /**
     * Generate a random nonce
     *
     * @return string
     */
    public static function generate_nonce() {
        return bin2hex( random_bytes( 16 ) );
    }

    /**
     * Check and record nonce (replay protection)
     *
     * @param string $nonce     Nonce value
     * @param string $device_id Device ID
     * @return bool True if nonce is fresh, false if replayed
     */
    public static function verify_nonce( $nonce, $device_id ) {
        global $wpdb;
        $tables = SPC_Database::get_table_names();

        // Check if nonce was already used
        $exists = $wpdb->get_var( $wpdb->prepare(
            "SELECT COUNT(*) FROM {$tables['nonces']} WHERE nonce = %s AND device_id = %s",
            $nonce,
            $device_id
        ) );

        if ( $exists > 0 ) {
            return false;
        }

        // Record the nonce
        $wpdb->insert(
            $tables['nonces'],
            array(
                'nonce'     => $nonce,
                'device_id' => $device_id,
            ),
            array( '%s', '%s' )
        );

        return true;
    }
}
