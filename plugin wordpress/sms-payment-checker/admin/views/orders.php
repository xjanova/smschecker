<?php
/**
 * Order approvals admin view
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
    <h1><?php esc_html_e( 'Order Approvals', 'sms-payment-checker' ); ?></h1>

    <!-- Filter Tabs -->
    <div class="spc-tabs">
        <?php
        $statuses = array(
            ''                => __( 'All', 'sms-payment-checker' ),
            'pending_review'  => __( 'Pending', 'sms-payment-checker' ),
            'auto_approved'   => __( 'Auto Approved', 'sms-payment-checker' ),
            'manually_approved' => __( 'Manual', 'sms-payment-checker' ),
            'rejected'        => __( 'Rejected', 'sms-payment-checker' ),
        );
        foreach ( $statuses as $key => $label ) :
            $active = ( ( $status ?? '' ) === $key ) ? 'spc-tab-active' : '';
            $url = add_query_arg( array( 'page' => 'spc-orders', 'status' => $key ), admin_url( 'admin.php' ) );
        ?>
            <a href="<?php echo esc_url( $url ); ?>" class="spc-tab <?php echo esc_attr( $active ); ?>">
                <?php echo esc_html( $label ); ?>
            </a>
        <?php endforeach; ?>
    </div>

    <!-- Orders Table -->
    <div class="spc-card">
        <?php if ( empty( $orders['data'] ) ) : ?>
            <p class="spc-empty"><?php esc_html_e( 'No orders found.', 'sms-payment-checker' ); ?></p>
        <?php else : ?>
            <table class="widefat striped">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th><?php esc_html_e( 'Bank', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Amount', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Type', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Order ID', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Status', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Confidence', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Date', 'sms-payment-checker' ); ?></th>
                        <th><?php esc_html_e( 'Actions', 'sms-payment-checker' ); ?></th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ( $orders['data'] as $order ) : ?>
                    <tr id="order-row-<?php echo esc_attr( $order->id ); ?>">
                        <td>#<?php echo esc_html( $order->id ); ?></td>
                        <td><strong><?php echo esc_html( $order->bank ?? '-' ); ?></strong></td>
                        <td>&#3647;<?php echo esc_html( number_format( (float) ( $order->amount ?? 0 ), 2 ) ); ?></td>
                        <td>
                            <?php if ( ( $order->type ?? '' ) === 'credit' ) : ?>
                                <span class="spc-badge spc-badge-green"><?php esc_html_e( 'Credit', 'sms-payment-checker' ); ?></span>
                            <?php else : ?>
                                <span class="spc-badge spc-badge-red"><?php esc_html_e( 'Debit', 'sms-payment-checker' ); ?></span>
                            <?php endif; ?>
                        </td>
                        <td>
                            <?php if ( $order->order_id ) : ?>
                                <?php if ( class_exists( 'WC_Order' ) ) : ?>
                                    <a href="<?php echo esc_url( admin_url( 'post.php?post=' . $order->order_id . '&action=edit' ) ); ?>">
                                        #<?php echo esc_html( $order->order_id ); ?>
                                    </a>
                                <?php else : ?>
                                    #<?php echo esc_html( $order->order_id ); ?>
                                <?php endif; ?>
                            <?php else : ?>
                                -
                            <?php endif; ?>
                        </td>
                        <td>
                            <?php
                            $badge_class = 'spc-badge-orange';
                            $status_label = ucfirst( str_replace( '_', ' ', $order->approval_status ) );
                            if ( in_array( $order->approval_status, array( 'auto_approved', 'manually_approved' ) ) ) {
                                $badge_class = 'spc-badge-green';
                            } elseif ( $order->approval_status === 'rejected' ) {
                                $badge_class = 'spc-badge-red';
                            }
                            ?>
                            <span class="spc-badge <?php echo esc_attr( $badge_class ); ?>">
                                <?php echo esc_html( $status_label ); ?>
                            </span>
                        </td>
                        <td>
                            <span class="spc-badge <?php echo $order->confidence === 'high' ? 'spc-badge-green' : 'spc-badge-orange'; ?>">
                                <?php echo esc_html( ucfirst( $order->confidence ) ); ?>
                            </span>
                        </td>
                        <td><?php echo esc_html( $order->created_at ); ?></td>
                        <td>
                            <?php if ( $order->approval_status === 'pending_review' ) : ?>
                                <button class="button button-small button-primary spc-approve-order" data-order-id="<?php echo esc_attr( $order->id ); ?>">
                                    <?php esc_html_e( 'Approve', 'sms-payment-checker' ); ?>
                                </button>
                                <button class="button button-small button-link-delete spc-reject-order" data-order-id="<?php echo esc_attr( $order->id ); ?>">
                                    <?php esc_html_e( 'Reject', 'sms-payment-checker' ); ?>
                                </button>
                            <?php endif; ?>
                        </td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>

            <!-- Pagination -->
            <?php if ( $orders['last_page'] > 1 ) : ?>
            <div class="spc-pagination">
                <?php for ( $i = 1; $i <= $orders['last_page']; $i++ ) : ?>
                    <?php
                    $url = add_query_arg( array(
                        'page'   => 'spc-orders',
                        'paged'  => $i,
                        'status' => $status,
                    ), admin_url( 'admin.php' ) );
                    ?>
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
