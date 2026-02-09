# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2026-02-09

### Added

#### Laravel Plugin (Both xmanstudio & Thaiprompt-Affiliate)
- **FCM Push Notifications**: Server sends Firebase Cloud Messaging push to Android app when new orders/bills arrive, replacing constant polling
- **FCM Admin Settings Page**: Web-based UI in admin panel to configure Firebase — upload Service Account JSON, set Project ID, toggle FCM on/off, test push button
- **Database-backed FCM Config**: FCM settings stored in `settings` table via Setting model, no need to edit `.env` files on server
- **FcmNotificationService**: Reads credentials from database first, falls back to config/env — supports both admin UI and traditional `.env` setup
- **NewOrderCreated / NewTransactionCreated Events**: Laravel events fired when new orders are created, triggering FCM push via listeners
- **SendNewOrderFcmNotification / SendNewTransactionFcmNotification Listeners**: Queue-compatible listeners that send FCM push on new order events

#### Android App
- **FCM Token Registration**: App registers its FCM token with each connected server on startup and token refresh
- **Push-triggered Sync**: When server sends FCM push, app immediately syncs instead of waiting for next polling interval
- **Reduced Polling**: Sync interval changed from 60s to 300s (5 minutes) since FCM handles real-time notifications
- **Green-to-red revert prevention**: Matched (green) bills no longer revert to red/pending on subsequent syncs
- **422 error handling**: Graceful handling of duplicate submission responses from server

### Fixed

#### Android App
- Bills from Thaiprompt-Affiliate now appear correctly (store filtering fix for admin/seller devices)
- xmanstudio `matched` → `auto_approved` status mapping fix

## [1.2.0] - 2025-02-01

### Added

#### Android App
- **Enhanced Dashboard**: Professional dashboard with Net Balance card (gradient border), System Overview grid (total messages, sync rate, connected servers, offline queue), sync progress bar, and improved server health section
- **TTS Customization**: Select TTS language (Thai/English/System), choose which content to read aloud (bank name, amount, type, time), and preview button to test TTS settings
- **Transaction History Screen**: Replaced SMS Matcher/Scan with read-only history view showing last 200 detected bank messages with filter (All/Credit/Debit), stats summary (detected/synced), and sync status badges
- **Lock Screen Notifications**: Heads-up floating alerts visible on lock screen with transaction details, session counters (detected/matched), and `VISIBILITY_PUBLIC` notification channel
- **Session Counters**: Foreground service notification shows running count of detected and matched transactions

#### WordPress Plugin (`plugin wordpress/sms-payment-checker/`)
- **Full WordPress/WooCommerce integration** — new standalone plugin matching all Laravel API endpoints
- **REST API** (`sms-payment/v1` namespace): notify, status, register-device, orders, device-settings, dashboard-stats, generate-amount, notifications
- **AES-256-GCM encryption** with HMAC-SHA256 signature verification and nonce replay protection
- **Device management** with QR code generation for Android app setup
- **Unique amount matching** — decimal suffix (0.01-0.99) reservation system for payment verification
- **WooCommerce integration** — auto-match payments with orders, auto-complete orders on approval
- **Admin dashboard** with statistics, device management, order approvals, notification history, and settings
- **Order approval workflow** — auto/manual modes with approve/reject actions
- **Multi-device support** with individual API keys per device
- **GPL-2.0 license** (Xman Studio)

### Fixed

#### Android App
- **Bottom navigation icon visibility** — active icon was invisible (gold on gold); changed to white icon on solid gold indicator
- **Screen crashes on refresh and SMS history** — replaced experimental `Badge`/`BadgedBox` with stable `Box` composables
- **CancellationException handling** — properly rethrow `CancellationException` in coroutine catch blocks to prevent swallowing cancellation signals
- **TTS thread safety** — moved all TTS operations to main thread using `withContext(Dispatchers.Main)` for reliable voice announcements

### Changed
- SMS Matcher screen transformed from scan/rule-based UI to read-only transaction history
- Dashboard ViewModel now injects `TransactionDao` directly for total/synced count queries
- Notification channel for transactions set to `VISIBILITY_PUBLIC` for lock screen display
- `versionCode` bumped to 7, `versionName` to "1.5.1"

## [1.1.0] - 2025-01-31

### Added

#### Android App
- **QR Code Scanner**: CameraX + ML Kit barcode scanning for server auto-configuration
- **Dark/Light Theme Toggle**: Full Material 3 theme switching (Dark, Light, System)
- **Thai/English Language Toggle**: Complete bilingual UI (Thai, English, System)
- **SMS Bank Matcher**: Custom sender-to-bank mapping with rule management
- **SMS Auto-Scan**: Reads existing SMS inbox to detect bank transactions using ContentResolver
- **Intelligent Bank Detection**: Heuristic detection for unknown senders using financial keywords + amount patterns
- **Auto-Match with Orders**: Compares credit transaction amounts with pending orders (0.01 tolerance)
- **TTS Voice Alerts**: Android built-in TextToSpeech for transaction announcements (Thai/English, free, no download)
- **Order Approval System**: Full order management with approve/reject/bulk-approve workflow
- **Dashboard Statistics**: Charts, daily breakdown, sync status with server
- **Multi-server Support**: Connect to multiple Laravel backends simultaneously
- **Premium UI Components**: GlassCard, GradientHeader, BankLogoCircle, SectionTitle
- **Settings Enhancements**: Device info, approval mode selection, TTS toggle, theme/language toggles

#### Laravel Plugin
- **QR Code Setup Page**: Web-based QR code generation for easy device configuration (qr-setup.blade.php)
- **QrConfigController**: Generates JSON config payload as QR code for Android app scanning
- **Order Approval Endpoints**: GET /orders, POST /orders/{id}/approve, POST /orders/{id}/reject, POST /orders/bulk-approve, GET /orders/sync
- **Device Settings Endpoints**: GET /device-settings, PUT /device-settings
- **Dashboard Statistics Endpoint**: GET /dashboard-stats with daily breakdown
- **Web Routes**: QR code page routes with auth middleware

### Fixed
- SMS Matcher screen crash on navigation (Hilt DI injection issue with Application parameter)
- SmsInboxScanner using Application instead of @ApplicationContext Context
- Proper error handling around rules loading, order fetching, and inbox scanning

### Security
- QR code page warns users not to share/screenshot API keys
- QR code page protected by Laravel auth middleware

## [1.0.0] - 2025-01-29

### Added

#### Android App
- Initial release of SMS Payment Checker Android app
- **BankSmsParser**: Parse SMS from 8 Thai banks (KBANK, SCB, KTB, BBL, GSB, BAY, TTB, PromptPay)
- **SmsBroadcastReceiver**: Real-time SMS interception with multi-part SMS support
- **SmsProcessingService**: Foreground service for background SMS processing
- **BootReceiver**: Auto-start monitoring after device reboot
- **CryptoManager**: AES-256-GCM encryption + HMAC-SHA256 signing
- **SecureStorage**: Android Keystore-backed encrypted storage
- **ApiClientFactory**: Multi-server Retrofit client with caching
- **TransactionRepository**: Local Room database + server sync logic
- **Dashboard Screen**: Real-time summary (today credit/debit, sync status, recent transactions)
- **Transaction List Screen**: Full history with filter (All/Income/Expense) and sync stats
- **Settings Screen**: Multi-server management, device info, monitoring toggle, bank info
- **Material 3 Theme**: Premium gold + dark/light theme support
- **Hilt DI**: Full dependency injection setup

#### Laravel Plugin
- **SmsPaymentController**: 5 API endpoints (notify, status, register, generate-amount, notifications)
- **SmsPaymentService**: Core service (decrypt, verify HMAC, process notification, auto-match)
- **VerifySmsCheckerDevice**: Middleware for API key + device authentication
- **SmsCheckerDevice**: Model with key generation and device management
- **SmsPaymentNotification**: Model with auto-match logic (unique amount + reference fallback)
- **UniquePaymentAmount**: Unique decimal suffix (0.01-0.99) reservation system
- **Database Migration**: 4 tables (devices, notifications, unique_amounts, nonces)
- **Configuration**: `config/smschecker.php` with env-based settings
- **Artisan Commands**: `smschecker:create-device`, `smschecker:cleanup`
- **Database Seeder**: Test device creation

#### Security
- AES-256-GCM payload encryption (matching between Android and PHP)
- HMAC-SHA256 request integrity verification
- Nonce-based replay attack prevention
- 5-minute timestamp window validation
- API Key + Device ID authentication
- Android Keystore secure key storage
- Encrypted SharedPreferences

#### DevOps
- GitHub Actions CI/CD for Android build
- GitHub Actions CI/CD for Laravel tests
- Comprehensive `.gitignore`
- Semantic versioning with VERSION file
- CONTRIBUTING.md with development guidelines

#### Documentation
- Full setup guide (SETUP.md)
- API reference (API.md)
- Security architecture (SECURITY.md)
- Bank SMS patterns (BANKS.md)
- README with architecture diagram

### Security
- Network security config: cleartext only for localhost/emulator
- ProGuard rules for production builds
- Secret key hidden from JSON serialization

[1.2.0]: https://github.com/xjanova/smschecker/releases/tag/v1.2.0
[1.1.0]: https://github.com/xjanova/smschecker/releases/tag/v1.1.0
[1.0.0]: https://github.com/xjanova/smschecker/releases/tag/v1.0.0
