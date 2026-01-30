# Contributing to SMS Payment Checker

## Development Setup

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Android Studio | 2023.2+ | Android app development |
| JDK | 21+ | Kotlin compilation |
| PHP | 8.1+ | Laravel plugin |
| Composer | 2.x | PHP dependency management |
| Node.js | 18+ | (optional) for Laravel frontend |

### Android App Development

```bash
# Open project
# Android Studio → File → Open → select SmsChecker/ root folder

# Sync Gradle
# File → Sync Project with Gradle Files

# Build
# Build → Make Project (Ctrl+F9)

# Run on device/emulator
# Run → Run 'app' (Shift+F10)
```

### Laravel Plugin Development

```bash
cd laravel-plugin

# Install test dependencies
composer install

# Run tests
vendor/bin/phpunit

# Code style check
vendor/bin/php-cs-fixer fix --dry-run
```

## Branch Strategy

```
main                ← production-ready releases
├── develop         ← integration branch
│   ├── feature/*   ← new features
│   ├── fix/*       ← bug fixes
│   └── docs/*      ← documentation updates
└── release/*       ← release candidates
```

### Branch Naming

- `feature/add-bank-support-uob` - New feature
- `fix/sms-parser-kbank-pattern` - Bug fix
- `docs/update-api-reference` - Documentation
- `release/1.1.0` - Release candidate

## Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Code style (no logic change) |
| `refactor` | Code refactoring |
| `test` | Adding/updating tests |
| `ci` | CI/CD changes |
| `chore` | Build process or tooling |

### Scopes

| Scope | Description |
|-------|-------------|
| `android` | Android app changes |
| `laravel` | Laravel plugin changes |
| `parser` | SMS parser changes |
| `security` | Security-related changes |
| `ui` | UI/UX changes |
| `api` | API changes |
| `docs` | Documentation |

### Examples

```
feat(parser): add UOB bank SMS pattern support
fix(android): handle multi-part SMS reassembly edge case
docs(api): update notification endpoint response format
ci(android): add APK artifact upload to workflow
```

## Pull Request Process

1. Create a branch from `develop`
2. Make your changes
3. Write/update tests
4. Update documentation if needed
5. Update CHANGELOG.md
6. Submit PR to `develop`
7. Wait for CI to pass
8. Request code review

### PR Title Format

```
[Android] feat: add UOB bank support
[Laravel] fix: unique amount collision handling
[Docs] update API reference for v1.1
```

## Code Style

### Kotlin (Android)
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktlint` for formatting
- Max line length: 120 characters
- Use Coroutines for async operations
- Use Flow for reactive data

### PHP (Laravel)
- Follow [PSR-12](https://www.php-fig.org/psr/psr-12/)
- Use `php-cs-fixer` for formatting
- Type hints required for all parameters and return types
- Use Laravel conventions for naming

## Adding a New Bank

To add support for a new bank:

### 1. Android - BankSmsParser.kt

```kotlin
// Add sender pattern
private val BANK_SENDERS = mapOf(
    // ... existing
    "NEW_BANK" to listOf("NewBank", "NEWBANK"),
)

// Add credit/debit patterns if unique format
```

### 2. Laravel - config/smschecker.php

```php
'supported_banks' => [
    // ... existing
    'NEW_BANK' => 'New Bank Name',
],
```

### 3. Tests

Add SMS samples for the new bank in:
- `android-app/app/src/test/` - Unit tests
- `laravel-plugin/tests/` - PHP tests

### 4. Documentation

Update `docs/BANKS.md` with:
- Bank name and code
- SMS sender addresses
- Example SMS messages
- Regex patterns used

## Versioning

We use [Semantic Versioning](https://semver.org/):

- **MAJOR** (x.0.0): Breaking API changes
- **MINOR** (0.x.0): New features (backward compatible)
- **PATCH** (0.0.x): Bug fixes

### Release Process

1. Update `VERSION` file
2. Update `CHANGELOG.md`
3. Update `app/build.gradle.kts` version
4. Create release branch: `release/x.y.z`
5. Create Git tag: `vx.y.z`
6. Create GitHub Release with APK artifact

## Reporting Issues

Use GitHub Issues with these labels:

| Label | Description |
|-------|-------------|
| `bug` | Something isn't working |
| `feature` | New feature request |
| `bank` | New bank support |
| `security` | Security vulnerability |
| `docs` | Documentation improvement |
| `android` | Android-specific |
| `laravel` | Laravel-specific |
