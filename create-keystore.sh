#!/bin/bash
# สร้าง Release Keystore สำหรับ SMS Checker
# รันด้วย: bash create-keystore.sh

KEYSTORE_FILE="smschecker-release.keystore"
KEYSTORE_ALIAS="smschecker-release"
KEYSTORE_PASSWORD="android123"
KEY_PASSWORD="android123"

echo "🔐 Creating Release Keystore for SMS Checker..."

# ใช้ keytool จาก JDK
keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEYSTORE_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=SMS Checker, OU=ThaiPrompt, O=ThaiPrompt, L=Bangkok, ST=Bangkok, C=TH"

if [ $? -eq 0 ]; then
    echo "✅ Keystore created successfully: $KEYSTORE_FILE"
    echo ""
    echo "📋 Keystore Information:"
    echo "  File: $KEYSTORE_FILE"
    echo "  Alias: $KEYSTORE_ALIAS"
    echo "  Password: $KEYSTORE_PASSWORD"
    echo "  Key Password: $KEY_PASSWORD"
    echo ""
    echo "⚠️ IMPORTANT: Keep this keystore file safe!"
    echo "   If you lose it, you cannot update your app anymore."
    echo ""
    echo "📝 Add to .gitignore:"
    echo "   *.keystore"
    echo ""
    echo "🔧 Next steps:"
    echo "   1. Add keystore to GitHub Secrets"
    echo "   2. Configure GitHub Actions with signing credentials"
else
    echo "❌ Failed to create keystore"
    exit 1
fi
