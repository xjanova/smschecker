<?php
/**
 * Devices admin view
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
    <h1><?php esc_html_e( 'Devices', 'sms-payment-checker' ); ?></h1>
    <p class="spc-subtitle"><?php esc_html_e( 'Manage connected Android devices', 'sms-payment-checker' ); ?></p>

    <!-- Add Device Form -->
    <div class="spc-card">
        <h2><?php esc_html_e( 'Add New Device', 'sms-payment-checker' ); ?></h2>
        <form id="spc-add-device-form" class="spc-form">
            <div class="spc-form-row">
                <label for="device_name"><?php esc_html_e( 'Device Name', 'sms-payment-checker' ); ?></label>
                <input type="text" id="device_name" name="device_name" placeholder="<?php esc_attr_e( 'e.g. Samsung Galaxy A54', 'sms-payment-checker' ); ?>" required />
                <button type="submit" class="button button-primary">
                    <span class="dashicons dashicons-plus-alt2"></span>
                    <?php esc_html_e( 'Create Device', 'sms-payment-checker' ); ?>
                </button>
            </div>
        </form>
        <div id="spc-new-device-result" style="display:none;" class="spc-alert spc-alert-success">
            <h3><?php esc_html_e( 'Device Created!', 'sms-payment-checker' ); ?></h3>
            <p><?php esc_html_e( 'Scan this QR code with the SMS Payment Checker app:', 'sms-payment-checker' ); ?></p>
            <div id="spc-qr-code" style="margin: 16px 0;"></div>
            <table class="spc-info-table">
                <tr>
                    <td><strong>API Key:</strong></td>
                    <td><code id="spc-new-api-key"></code></td>
                </tr>
                <tr>
                    <td><strong>Secret Key:</strong></td>
                    <td><code id="spc-new-secret-key"></code></td>
                </tr>
                <tr>
                    <td><strong>Device ID:</strong></td>
                    <td><code id="spc-new-device-id"></code></td>
                </tr>
            </table>
            <p class="description"><?php esc_html_e( 'Save these keys! The secret key will not be shown again.', 'sms-payment-checker' ); ?></p>
        </div>
    </div>

    <!-- Devices List -->
    <div class="spc-card">
        <h2><?php esc_html_e( 'Connected Devices', 'sms-payment-checker' ); ?> (<?php echo count( $devices ); ?>)</h2>

        <?php if ( empty( $devices ) ) : ?>
            <p class="spc-empty"><?php esc_html_e( 'No devices configured yet. Create one above.', 'sms-payment-checker' ); ?></p>
        <?php else : ?>
            <table class="widefat striped">
                <thead>
                    <tr>
                        <th><?php esc_html_e( 'Name', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Device ID', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Platform', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Version', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Status', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Last Active', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Actions', 'sms-payment-checker' ); ?></th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ( $devices as $device ) : ?>
                    <tr id="device-row-<?php echo esc_attr( $device->id ); ?>">
                        <td><strong><?php echo esc_html( $device->device_name ); ?></strong></td>
                        <td><code><?php echo esc_html( substr( $device->device_id, 0, 8 ) . '...' ); ?></code></td>
                        <td><?php echo esc_html( ucfirst( $device->platform ) ); ?></td>
                        <td><?php echo esc_html( $device->app_version ?: '-' ); ?></td>
                        <td>
                            <?php
                            $status_class = 'spc-badge-green';
                            if ( $device->status === 'inactive' ) $status_class = 'spc-badge-orange';
                            if ( $device->status === 'blocked' ) $status_class = 'spc-badge-red';
                            ?>
                            <span class="spc-badge <?php echo esc_attr( $status_class ); ?>">
                                <?php echo esc_html( ucfirst( $device->status ) ); ?>
                            </span>
                        </td>
                        <td><?php echo $device->last_active_at ? esc_html( human_time_diff( strtotime( $device->last_active_at ) ) . ' ago' ) : '-'; ?></td>
                        <td>
                            <button class="button button-small spc-show-qr" data-device-id="<?php echo esc_attr( $device->id ); ?>" data-qr="<?php echo esc_attr( wp_json_encode( SPC_Device_Manager::get_qr_data( $device->id ) ) ); ?>">
                                <span class="dashicons dashicons-visibility"></span> QR
                            </button>
                            <?php if ( $device->status === 'active' ) : ?>
                                <button class="button button-small spc-toggle-device" data-device-id="<?php echo esc_attr( $device->id ); ?>" data-status="inactive">
                                    <?php esc_html_e( 'Deactivate', 'sms-payment-checker' ); ?>
                                </button>
                            <?php else : ?>
                                <button class="button button-small button-primary spc-toggle-device" data-device-id="<?php echo esc_attr( $device->id ); ?>" data-status="active">
                                    <?php esc_html_e( 'Activate', 'sms-payment-checker' ); ?>
                                </button>
                            <?php endif; ?>
                            <button class="button button-small button-link-delete spc-delete-device" data-device-id="<?php echo esc_attr( $device->id ); ?>">
                                <span class="dashicons dashicons-trash"></span>
                            </button>
                        </td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        <?php endif; ?>
    </div>

    <!-- QR Modal -->
    <div id="spc-qr-modal" style="display:none;" class="spc-modal-backdrop">
        <div class="spc-modal">
            <h3><?php esc_html_e( 'Scan QR Code', 'sms-payment-checker' ); ?></h3>
            <p><?php esc_html_e( 'Open SMS Payment Checker app > Settings > Scan QR', 'sms-payment-checker' ); ?></p>
            <div id="spc-modal-qr"></div>
            <button class="button spc-close-modal"><?php esc_html_e( 'Close', 'sms-payment-checker' ); ?></button>
        </div>
    </div>
</div>
