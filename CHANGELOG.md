# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[1.0.0]: https://github.com/xjanova/smschecker/releases/tag/v1.0.0
