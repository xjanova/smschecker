<?php
/**
 * Dashboard admin view
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
    <h1><?php esc_html_e( 'SMS Payment Checker', 'sms-payment-checker' ); ?></h1>
    <p class="spc-subtitle"><?php esc_html_e( 'Dashboard Overview', 'sms-payment-checker' ); ?></p>

    <!-- Stats Cards -->
    <div class="spc-stats-grid">
        <div class="spc-stat-card spc-stat-total">
            <div class="spc-stat-icon">
                <span class="dashicons dashicons-chart-bar"></span>
            </div>
            <div class="spc-stat-content">
                <h3><?php echo esc_html( $stats['total_orders'] ); ?></h3>
                <span><?php esc_html_e( 'Total Orders', 'sms-payment-checker' ); ?></span>
            </div>
        </div>

        <div class="spc-stat-card spc-stat-approved">
            <div class="spc-stat-icon">
                <span class="dashicons dashicons-yes-alt"></span>
            </div>
            <div class="spc-stat-content">
                <h3><?php echo esc_html( $stats['auto_approved'] + $stats['manually_approved'] ); ?></h3>
                <span><?php esc_html_e( 'Approved', 'sms-payment-checker' ); ?></span>
            </div>
        </div>

        <div class="spc-stat-card spc-stat-pending">
            <div class="spc-stat-icon">
                <span class="dashicons dashicons-clock"></span>
            </div>
            <div class="spc-stat-content">
                <h3><?php echo esc_html( $stats['pending_review'] ); ?></h3>
                <span><?php esc_html_e( 'Pending Review', 'sms-payment-checker' ); ?></span>
            </div>
        </div>

        <div class="spc-stat-card spc-stat-amount">
            <div class="spc-stat-icon">
                <span class="dashicons dashicons-money-alt"></span>
            </div>
            <div class="spc-stat-content">
                <h3>&#3647;<?php echo esc_html( number_format( $stats['total_amount'], 2 ) ); ?></h3>
                <span><?php esc_html_e( 'Total Amount', 'sms-payment-checker' ); ?></span>
            </div>
        </div>
    </div>

    <!-- Breakdown -->
    <div class="spc-card">
        <h2><?php esc_html_e( 'Approval Breakdown', 'sms-payment-checker' ); ?></h2>
        <table class="spc-breakdown-table">
            <tr>
                <td><span class="spc-dot spc-dot-green"></span> <?php esc_html_e( 'Auto Approved', 'sms-payment-checker' ); ?></td>
                <td><strong><?php echo esc_html( $stats['auto_approved'] ); ?></strong></td>
            </tr>
            <tr>
                <td><span class="spc-dot spc-dot-blue"></span> <?php esc_html_e( 'Manually Approved', 'sms-payment-checker' ); ?></td>
                <td><strong><?php echo esc_html( $stats['manually_approved'] ); ?></strong></td>
            </tr>
            <tr>
                <td><span class="spc-dot spc-dot-orange"></span> <?php esc_html_e( 'Pending Review', 'sms-payment-checker' ); ?></td>
                <td><strong><?php echo esc_html( $stats['pending_review'] ); ?></strong></td>
            </tr>
            <tr>
                <td><span class="spc-dot spc-dot-red"></span> <?php esc_html_e( 'Rejected', 'sms-payment-checker' ); ?></td>
                <td><strong><?php echo esc_html( $stats['rejected'] ); ?></strong></td>
            </tr>
        </table>
    </div>

    <?php if ( ! empty( $stats['daily_breakdown'] ) ) : ?>
    <div class="spc-card">
        <h2><?php esc_html_e( 'Last 7 Days', 'sms-payment-checker' ); ?></h2>
        <table class="widefat striped">
            <thead>
                <tr>
                    <th><?php esc_html_e( 'Date', 'sms-payment-checker' ); ?></th>
                    <th><?php esc_html_e( 'Total', 'sms-payment-checker' ); ?></th>
                    <th><?php esc_html_e( 'Approved', 'sms-payment-checker' ); ?></th>
                    <th><?php esc_html_e( 'Rejected', 'sms-payment-checker' ); ?></th>
                    <th><?php esc_html_e( 'Amount', 'sms-payment-checker' ); ?></th>
                </tr>
            </thead>
            <tbody>
                <?php foreach ( $stats['daily_breakdown'] as $day ) : ?>
                <tr>
                    <td><?php echo esc_html( $day['date'] ); ?></td>
                    <td><?php echo esc_html( $day['count'] ); ?></td>
                    <td><span class="spc-badge spc-badge-green"><?php echo esc_html( $day['approved'] ); ?></span></td>
                    <td><span class="spc-badge spc-badge-red"><?php echo esc_html( $day['rejected'] ); ?></span></td>
                    <td>&#3647;<?php echo esc_html( number_format( $day['amount'], 2 ) ); ?></td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
    <?php endif; ?>

    <div class="spc-footer">
        <p>SMS Payment Checker v<?php echo esc_html( SPC_VERSION ); ?> &mdash; &copy; <?php echo esc_html( gmdate( 'Y' ) ); ?> Xman Studio</p>
    </div>
</div>
