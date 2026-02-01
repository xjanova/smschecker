=== SMS Payment Checker ===
Contributors: xmanstudio
Tags: sms, payment, woocommerce, bank, thailand, payment-verification
Requires at least: 5.8
Tested up to: 6.4
Requires PHP: 7.4
Stable tag: 1.0.0
License: GPLv2 or later
License URI: https://www.gnu.org/licenses/gpl-2.0.html

Receive and verify SMS payment notifications from Android devices. Auto-match with WooCommerce orders using unique payment amounts.

== Description ==

SMS Payment Checker connects your WordPress/WooCommerce site with the Android SMS Payment Checker app to automatically verify bank transfer payments.

**Key Features:**

* Receive encrypted SMS payment notifications from Android devices
* Auto-match payments with WooCommerce orders using unique amounts
* Support for 15+ Thai banks (KBANK, SCB, KTB, BBL, GSB, BAY, TTB, etc.)
* AES-256-GCM encryption with HMAC signature verification
* QR code device setup for easy app configuration
* Order approval workflow (auto/manual modes)
* Dashboard with statistics and daily breakdown
* Multi-device support with individual API keys
* Nonce replay protection
* WooCommerce integration (auto-complete orders)

**Supported Banks:**
KBANK, SCB, KTB, BBL, GSB, BAY, TTB, PromptPay, CIMB, KKP, LH, TISCO, UOB, ICBC, BAAC

== Installation ==

1. Upload the `sms-payment-checker` folder to `/wp-content/plugins/`
2. Activate the plugin through the 'Plugins' menu
3. Go to 'SMS Payment' > 'Devices' to create a new device
4. Scan the QR code with the Android app
5. Configure settings under 'SMS Payment' > 'Settings'

== Frequently Asked Questions ==

= Do I need WooCommerce? =
No, the plugin works standalone. WooCommerce integration is optional but enables automatic order matching.

= Is the data encrypted? =
Yes, all payment data is encrypted with AES-256-GCM and verified with HMAC-SHA256 signatures.

= How does unique amount matching work? =
The plugin adds a small decimal suffix (0.01-0.99) to order amounts. When a customer pays the unique amount, the system matches it to the correct order.

== Changelog ==

= 1.0.0 =
* Initial release
* Full REST API compatible with Android app
* WooCommerce integration
* Device management with QR codes
* Order approval workflow
* Dashboard statistics

== Upgrade Notice ==

= 1.0.0 =
Initial release of SMS Payment Checker for WordPress.
