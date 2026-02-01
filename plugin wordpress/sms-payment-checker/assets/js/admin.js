/**
 * SMS Payment Checker - Admin JavaScript
 *
 * @package SmsPaymentChecker
 * @author Xman Studio
 * @copyright 2025 Xman Studio
 */

(function($) {
    'use strict';

    // ═══ Create Device ═══
    $('#spc-add-device-form').on('submit', function(e) {
        e.preventDefault();
        var name = $('#device_name').val().trim();
        if (!name) return;

        var $btn = $(this).find('button[type="submit"]');
        $btn.prop('disabled', true).text('Creating...');

        $.ajax({
            url: spcAdmin.ajax_url,
            method: 'POST',
            data: {
                action: 'spc_create_device',
                nonce: spcAdmin.nonce,
                device_name: name
            },
            success: function(response) {
                if (response.success) {
                    var d = response.data;
                    $('#spc-new-api-key').text(d.api_key);
                    $('#spc-new-secret-key').text(d.secret_key);
                    $('#spc-new-device-id').text(d.device_id);
                    $('#spc-new-device-result').slideDown();

                    // Generate QR code
                    var qrData = JSON.stringify({
                        type: 'sms_checker_config',
                        version: 2,
                        url: spcAdmin.rest_url,
                        apiKey: d.api_key,
                        secretKey: d.secret_key,
                        deviceName: d.device_name,
                        deviceId: d.device_id
                    });

                    if (typeof QRCode !== 'undefined') {
                        var canvas = document.createElement('canvas');
                        QRCode.toCanvas(canvas, qrData, { width: 200 }, function() {
                            $('#spc-qr-code').html('').append(canvas);
                        });
                    }

                    $('#device_name').val('');

                    // Reload after 3 seconds to show new device in list
                    setTimeout(function() { location.reload(); }, 3000);
                } else {
                    alert(response.data || spcAdmin.strings.error);
                }
            },
            error: function() {
                alert(spcAdmin.strings.error);
            },
            complete: function() {
                $btn.prop('disabled', false).html('<span class="dashicons dashicons-plus-alt2"></span> Create Device');
            }
        });
    });

    // ═══ Delete Device ═══
    $(document).on('click', '.spc-delete-device', function() {
        if (!confirm(spcAdmin.strings.confirm_delete)) return;

        var id = $(this).data('device-id');
        $.ajax({
            url: spcAdmin.ajax_url,
            method: 'POST',
            data: {
                action: 'spc_delete_device',
                nonce: spcAdmin.nonce,
                device_id: id
            },
            success: function(response) {
                if (response.success) {
                    $('#device-row-' + id).fadeOut(300, function() { $(this).remove(); });
                }
            }
        });
    });

    // ═══ Toggle Device ═══
    $(document).on('click', '.spc-toggle-device', function() {
        var id = $(this).data('device-id');
        var status = $(this).data('status');
        $.ajax({
            url: spcAdmin.ajax_url,
            method: 'POST',
            data: {
                action: 'spc_toggle_device',
                nonce: spcAdmin.nonce,
                device_id: id,
                status: status
            },
            success: function() {
                location.reload();
            }
        });
    });

    // ═══ Show QR Modal ═══
    $(document).on('click', '.spc-show-qr', function() {
        var qrData = JSON.stringify($(this).data('qr'));
        var $modal = $('#spc-qr-modal');
        var $qr = $('#spc-modal-qr').html('');

        if (typeof QRCode !== 'undefined') {
            var canvas = document.createElement('canvas');
            QRCode.toCanvas(canvas, qrData, { width: 250 }, function() {
                $qr.append(canvas);
            });
        } else {
            $qr.html('<p>QR Code library not loaded.</p>');
        }

        $modal.show();
    });

    $(document).on('click', '.spc-close-modal, .spc-modal-backdrop', function(e) {
        if (e.target === this) {
            $('#spc-qr-modal').hide();
        }
    });

    // ═══ Save Settings ═══
    $('#spc-settings-form').on('submit', function(e) {
        e.preventDefault();
        var $btn = $(this).find('button[type="submit"]');
        $btn.prop('disabled', true).text('Saving...');

        var data = $(this).serialize();
        data += '&action=spc_save_settings&nonce=' + spcAdmin.nonce;

        $.ajax({
            url: spcAdmin.ajax_url,
            method: 'POST',
            data: data,
            success: function(response) {
                if (response.success) {
                    // Show success notice
                    var $notice = $('<div class="notice notice-success is-dismissible"><p>' + spcAdmin.strings.saved + '</p></div>');
                    $('.spc-wrap h1').after($notice);
                    setTimeout(function() { $notice.fadeOut(); }, 3000);
                }
            },
            complete: function() {
                $btn.prop('disabled', false).text('Save Settings');
            }
        });
    });

    // ═══ Approve Order ═══
    $(document).on('click', '.spc-approve-order', function() {
        if (!confirm(spcAdmin.strings.confirm_approve)) return;

        var id = $(this).data('order-id');
        var $row = $('#order-row-' + id);

        $.ajax({
            url: spcAdmin.ajax_url,
            method: 'POST',
            data: {
                action: 'spc_approve_order',
                nonce: spcAdmin.nonce,
                order_id: id
            },
            success: function() {
                $row.find('.spc-badge-orange').removeClass('spc-badge-orange').addClass('spc-badge-green').text('Manually Approved');
                $row.find('.spc-approve-order, .spc-reject-order').remove();
            }
        });
    });

    // ═══ Reject Order ═══
    $(document).on('click', '.spc-reject-order', function() {
        var reason = prompt('Rejection reason (optional):');
        if (reason === null) return; // cancelled

        var id = $(this).data('order-id');
        var $row = $('#order-row-' + id);

        $.ajax({
            url: spcAdmin.ajax_url,
            method: 'POST',
            data: {
                action: 'spc_reject_order',
                nonce: spcAdmin.nonce,
                order_id: id,
                reason: reason
            },
            success: function() {
                $row.find('.spc-badge-orange').removeClass('spc-badge-orange').addClass('spc-badge-red').text('Rejected');
                $row.find('.spc-approve-order, .spc-reject-order').remove();
            }
        });
    });

})(jQuery);
