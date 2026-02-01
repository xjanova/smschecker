<?php
/**
 * Settings admin view
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit;
}
?>
<div class="wrap spc-wrap">
    <h1><?php esc_html_e( 'Settings', 'sms-payment-checker' ); ?></h1>
    <p class="spc-subtitle"><?php esc_html_e( 'Configure SMS Payment Checker behavior', 'sms-payment-checker' ); ?></p>

    <form id="spc-settings-form">

        <!-- General Settings -->
        <div class="spc-card">
            <h2><?php esc_html_e( 'General', 'sms-payment-checker' ); ?></h2>
            <table class="form-table">
                <tr>
                    <th scope="row">
                        <label for="approval_mode"><?php esc_html_e( 'Approval Mode', 'sms-payment-checker' ); ?></label>
                    </th>
                    <td>
                        <select id="approval_mode" name="approval_mode">
                            <option value="auto" <?php selected( $settings['approval_mode'] ?? 'auto', 'auto' ); ?>>
                                <?php esc_html_e( 'Auto Approve', 'sms-payment-checker' ); ?>
                            </option>
                            <option value="manual" <?php selected( $settings['approval_mode'] ?? 'auto', 'manual' ); ?>>
                                <?php esc_html_e( 'Manual Review', 'sms-payment-checker' ); ?>
                            </option>
                        </select>
                        <p class="description"><?php esc_html_e( 'Auto: matched payments are approved automatically. Manual: all matches require manual review.', 'sms-payment-checker' ); ?></p>
                    </td>
                </tr>
                <tr>
                    <th scope="row">
                        <label for="unique_amount_expiry"><?php esc_html_e( 'Unique Amount Expiry (minutes)', 'sms-payment-checker' ); ?></label>
                    </th>
                    <td>
                        <input type="number" id="unique_amount_expiry" name="unique_amount_expiry"
                               value="<?php echo esc_attr( $settings['unique_amount_expiry'] ?? 30 ); ?>"
                               min="5" max="60" />
                        <p class="description"><?php esc_html_e( 'How long a reserved unique payment amount stays active.', 'sms-payment-checker' ); ?></p>
                    </td>
                </tr>
                <tr>
                    <th scope="row">
                        <label for="timestamp_tolerance"><?php esc_html_e( 'Timestamp Tolerance (seconds)', 'sms-payment-checker' ); ?></label>
                    </th>
                    <td>
                        <input type="number" id="timestamp_tolerance" name="timestamp_tolerance"
                               value="<?php echo esc_attr( $settings['timestamp_tolerance'] ?? 300 ); ?>"
                               min="60" max="600" />
                        <p class="description"><?php esc_html_e( 'Maximum allowed clock difference between device and server.', 'sms-payment-checker' ); ?></p>
                    </td>
                </tr>
            </table>
        </div>

        <!-- Security Settings -->
        <div class="spc-card">
            <h2><?php esc_html_e( 'Security', 'sms-payment-checker' ); ?></h2>
            <table class="form-table">
                <tr>
                    <th scope="row">
                        <label for="rate_limit_per_minute"><?php esc_html_e( 'Rate Limit (per minute)', 'sms-payment-checker' ); ?></label>
                    </th>
                    <td>
                        <input type="number" id="rate_limit_per_minute" name="rate_limit_per_minute"
                               value="<?php echo esc_attr( $settings['rate_limit_per_minute'] ?? 30 ); ?>"
                               min="5" max="100" />
                    </td>
                </tr>
                <tr>
                    <th scope="row">
                        <label for="nonce_expiry_hours"><?php esc_html_e( 'Nonce Expiry (hours)', 'sms-payment-checker' ); ?></label>
                    </th>
                    <td>
                        <input type="number" id="nonce_expiry_hours" name="nonce_expiry_hours"
                               value="<?php echo esc_attr( $settings['nonce_expiry_hours'] ?? 24 ); ?>"
                               min="1" max="72" />
                    </td>
                </tr>
                <tr>
                    <th scope="row">
                        <label for="max_pending_per_amount"><?php esc_html_e( 'Max Suffixes Per Amount', 'sms-payment-checker' ); ?></label>
                    </th>
                    <td>
                        <input type="number" id="max_pending_per_amount" name="max_pending_per_amount"
                               value="<?php echo esc_attr( $settings['max_pending_per_amount'] ?? 99 ); ?>"
                               min="10" max="99" />
                    </td>
                </tr>
            </table>
        </div>

        <!-- Integration Settings -->
        <div class="spc-card">
            <h2><?php esc_html_e( 'Integrations', 'sms-payment-checker' ); ?></h2>
            <table class="form-table">
                <tr>
                    <th scope="row"><?php esc_html_e( 'WooCommerce Integration', 'sms-payment-checker' ); ?></th>
                    <td>
                        <label>
                            <input type="checkbox" name="woocommerce_integration" value="1"
                                <?php checked( $settings['woocommerce_integration'] ?? true ); ?> />
                            <?php esc_html_e( 'Auto-complete WooCommerce orders when payment matches', 'sms-payment-checker' ); ?>
                        </label>
                        <?php if ( ! class_exists( 'WooCommerce' ) ) : ?>
                            <p class="description" style="color: #d63638;"><?php esc_html_e( 'WooCommerce is not installed. Install WooCommerce to use this feature.', 'sms-payment-checker' ); ?></p>
                        <?php endif; ?>
                    </td>
                </tr>
                <tr>
                    <th scope="row"><?php esc_html_e( 'Auto Confirm Matches', 'sms-payment-checker' ); ?></th>
                    <td>
                        <label>
                            <input type="checkbox" name="auto_confirm_matched" value="1"
                                <?php checked( $settings['auto_confirm_matched'] ?? true ); ?> />
                            <?php esc_html_e( 'Automatically confirm matched notifications', 'sms-payment-checker' ); ?>
                        </label>
                    </td>
                </tr>
                <tr>
                    <th scope="row"><?php esc_html_e( 'Notify on Match', 'sms-payment-checker' ); ?></th>
                    <td>
                        <label>
                            <input type="checkbox" name="notify_on_match" value="1"
                                <?php checked( $settings['notify_on_match'] ?? true ); ?> />
                            <?php esc_html_e( 'Send email notification when a payment matches', 'sms-payment-checker' ); ?>
                        </label>
                    </td>
                </tr>
            </table>
        </div>

        <!-- API Info -->
        <div class="spc-card">
            <h2><?php esc_html_e( 'API Information', 'sms-payment-checker' ); ?></h2>
            <table class="spc-info-table">
                <tr>
                    <td><strong><?php esc_html_e( 'REST API Base:', 'sms-payment-checker' ); ?></strong></td>
                    <td><code><?php echo esc_html( rest_url( 'sms-payment/v1/' ) ); ?></code></td>
                </tr>
                <tr>
                    <td><strong><?php esc_html_e( 'Notify Endpoint:', 'sms-payment-checker' ); ?></strong></td>
                    <td><code>POST <?php echo esc_html( rest_url( 'sms-payment/v1/notify' ) ); ?></code></td>
                </tr>
                <tr>
                    <td><strong><?php esc_html_e( 'Generate Amount:', 'sms-payment-checker' ); ?></strong></td>
                    <td><code>POST <?php echo esc_html( rest_url( 'sms-payment/v1/generate-amount' ) ); ?></code></td>
                </tr>
                <tr>
                    <td><strong><?php esc_html_e( 'Plugin Version:', 'sms-payment-checker' ); ?></strong></td>
                    <td><?php echo esc_html( SPC_VERSION ); ?></td>
                </tr>
            </table>
        </div>

        <p class="submit">
            <button type="submit" class="button button-primary button-hero">
                <?php esc_html_e( 'Save Settings', 'sms-payment-checker' ); ?>
            </button>
        </p>
    </form>

    <div class="spc-footer">
        <p>SMS Payment Checker v<?php echo esc_html( SPC_VERSION ); ?> &mdash; &copy; <?php echo esc_html( gmdate( 'Y' ) ); ?> Xman Studio</p>
    </div>
</div>
