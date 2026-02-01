<?php
/**
 * Notifications admin view
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
    <h1><?php esc_html_e( 'Payment Notifications', 'sms-payment-checker' ); ?></h1>
    <p class="spc-subtitle"><?php esc_html_e( 'All incoming SMS payment notifications from devices', 'sms-payment-checker' ); ?></p>

    <div class="spc-card">
        <?php if ( empty( $notifications ) ) : ?>
            <p class="spc-empty"><?php esc_html_e( 'No notifications received yet.', 'sms-payment-checker' ); ?></p>
        <?php else : ?>
            <table class="widefat striped">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th><?php esc_html_e( 'Bank', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Type', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Amount', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Reference', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Status', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Matched Order', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Device', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Date', 'sms-payment-checker' ); ?></th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ( $notifications as $n ) : ?>
                    <tr>
                        <td>#<?php echo esc_html( $n->id ); ?></td>
                        <td><strong><?php echo esc_html( $n->bank ); ?></strong></td>
                        <td>
                            <?php if ( $n->type === 'credit' ) : ?>
                                <span class="spc-badge spc-badge-green"><?php esc_html_e( 'Credit', 'sms-payment-checker' ); ?></span>
                            <?php else : ?>
                                <span class="spc-badge spc-badge-red"><?php esc_html_e( 'Debit', 'sms-payment-checker' ); ?></span>
                            <?php endif; ?>
                        </td>
                        <td>&#3647;<?php echo esc_html( number_format( (float) $n->amount, 2 ) ); ?></td>
                        <td><code><?php echo esc_html( $n->reference_number ?: '-' ); ?></code></td>
                        <td>
                            <?php
                            $badge = 'spc-badge-orange';
                            if ( $n->status === 'matched' ) $badge = 'spc-badge-green';
                            if ( $n->status === 'rejected' || $n->status === 'expired' ) $badge = 'spc-badge-red';
                            ?>
                            <span class="spc-badge <?php echo esc_attr( $badge ); ?>">
                                <?php echo esc_html( ucfirst( $n->status ) ); ?>
                            </span>
                        </td>
                        <td><?php echo $n->matched_order_id ? '#' . esc_html( $n->matched_order_id ) : '-'; ?></td>
                        <td><code><?php echo esc_html( substr( $n->device_id, 0, 8 ) . '...' ); ?></code></td>
                        <td><?php echo esc_html( $n->created_at ); ?></td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>

            <!-- Pagination -->
            <?php
            $total_pages = ceil( $total / $per_page );
            if ( $total_pages > 1 ) :
            ?>
            <div class="spc-pagination">
                <?php for ( $i = 1; $i <= $total_pages; $i++ ) : ?>
                    <?php $url = add_query_arg( array( 'page' => 'spc-notifications', 'paged' => $i ), admin_url( 'admin.php' ) ); ?>
                    <a href="<?php echo esc_url( $url ); ?>"
                       class="button button-small <?php echo $i === $page ? 'button-primary' : ''; ?>">
                        <?php echo esc_html( $i ); ?>
                    </a>
                <?php endfor; ?>
            </div>
            <?php endif; ?>
        <?php endif; ?>
    </div>
</div>
