<?php
/**
 * Plugin Name: SMS Payment Checker
 * Plugin URI: https://github.com/xman-studio/sms-payment-checker
 * Description: Receive and verify SMS payment notifications from Android devices. Supports auto-matching with WooCommerce orders using unique payment amounts.
 * Version: 1.0.0
 * Author: Xman Studio
 * Author URI: https://xman.studio
 * License: GPL-2.0-or-later
 * License URI: https://www.gnu.org/licenses/gpl-2.0.html
 * Text Domain: sms-payment-checker
 * Domain Path: /languages
 * Requires at least: 5.8
 * Requires PHP: 7.4
 * WC requires at least: 5.0
 * WC tested up to: 8.0
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 * @license GPL-2.0-or-later
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}

// Plugin constants
define( 'SPC_VERSION', '1.0.0' );
define( 'SPC_PLUGIN_FILE', __FILE__ );
define( 'SPC_PLUGIN_DIR', plugin_dir_path( __FILE__ ) );
define( 'SPC_PLUGIN_URL', plugin_dir_url( __FILE__ ) );
define( 'SPC_PLUGIN_BASENAME', plugin_basename( __FILE__ ) );

/**
 * Main plugin class
 */
final class SmsPaymentChecker {

    /**
     * Singleton instance
     */
    private static $instance = null;

    /**
     * Get singleton instance
     */
    public static function instance() {
        if ( is_null( self::$instance ) ) {
            self::$instance = new self();
        }
        return self::$instance;
    }

    /**
     * Constructor
     */
    private function __construct() {
        $this->includes();
        $this->init_hooks();
    }

    /**
     * Include required files
     */
    private function includes() {
        require_once SPC_PLUGIN_DIR . 'includes/class-spc-database.php';
        require_once SPC_PLUGIN_DIR . 'includes/class-spc-crypto.php';
        require_once SPC_PLUGIN_DIR . 'includes/class-spc-device-manager.php';
        require_once SPC_PLUGIN_DIR . 'includes/class-spc-payment-service.php';
        require_once SPC_PLUGIN_DIR . 'includes/class-spc-unique-amount.php';
        require_once SPC_PLUGIN_DIR . 'api/class-spc-rest-controller.php';

        if ( is_admin() ) {
            require_once SPC_PLUGIN_DIR . 'admin/class-spc-admin.php';
        }
    }

    /**
     * Initialize hooks
     */
    private function init_hooks() {
        register_activation_hook( SPC_PLUGIN_FILE, array( 'SPC_Database', 'activate' ) );
        register_deactivation_hook( SPC_PLUGIN_FILE, array( $this, 'deactivate' ) );

        add_action( 'rest_api_init', array( 'SPC_REST_Controller', 'register_routes' ) );
        add_action( 'init', array( $this, 'load_textdomain' ) );
        add_action( 'wp_loaded', array( $this, 'schedule_cleanup' ) );

        if ( is_admin() ) {
            SPC_Admin::init();
        }
    }

    /**
     * Load plugin textdomain
     */
    public function load_textdomain() {
        load_plugin_textdomain( 'sms-payment-checker', false, dirname( SPC_PLUGIN_BASENAME ) . '/languages/' );
    }

    /**
     * Schedule cleanup cron
     */
    public function schedule_cleanup() {
        if ( ! wp_next_scheduled( 'spc_cleanup_event' ) ) {
            wp_schedule_event( time(), 'hourly', 'spc_cleanup_event' );
        }
        add_action( 'spc_cleanup_event', array( 'SPC_Payment_Service', 'cleanup' ) );
    }

    /**
     * Deactivation hook
     */
    public function deactivate() {
        wp_clear_scheduled_hook( 'spc_cleanup_event' );
    }
}

/**
 * Initialize plugin
 */
function sms_payment_checker() {
    return SmsPaymentChecker::instance();
}

add_action( 'plugins_loaded', 'sms_payment_checker' );
