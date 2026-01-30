# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[1.1.0]: https://github.com/xjanova/smschecker/releases/tag/v1.1.0
[1.0.0]: https://github.com/xjanova/smschecker/releases/tag/v1.0.0
