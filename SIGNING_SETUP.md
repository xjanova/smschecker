# 🔐 Release Signing Setup

วิธีตั้งค่า Release Signing เพื่อให้อัพเดทแอพได้โดยไม่ต้องถอน

## ❌ ปัญหาปัจจุบัน

- Release APK ใช้ **debug key** (เปลี่ยนได้ตลอด)
- ทุกครั้งที่ build ใหม่ → **ต้องถอนแอพก่อน** → **ข้อมูลหาย**

## ✅ วิธีแก้ไข

สร้าง **Release Keystore** แบบถาวร → ใช้คีย์เดียวกันตลอด → อัพเดทได้โดยไม่ต้องถอน

---

## 📝 ขั้นตอนที่ 1: สร้าง Release Keystore

### วิธีที่ 1: ใช้ Android Studio (แนะนำ)

1. เปิด Android Studio
2. เมนู **Build** → **Generate Signed Bundle / APK**
3. เลือก **APK** → **Next**
4. คลิก **Create new...** (ข้าง Key store path)
5. กรอกข้อมูล:
   ```
   Key store path: D:\Code\SmsChecker\smschecker-release.keystore
   Password: android123
   Confirm: android123

   Alias: smschecker-release
   Password: android123
   Confirm: android123
   Validity (years): 27

   Certificate:
   First and Last Name: SMS Checker
   Organizational Unit: ThaiPrompt
   Organization: ThaiPrompt
   City or Locality: Bangkok
   State or Province: Bangkok
   Country Code: TH
   ```
6. คลิก **OK** → **Next** → **Finish**

### วิธีที่ 2: ใช้ Command Line

```bash
# Windows (ใช้ Command Prompt หรือ PowerShell)
cd D:\Code\SmsChecker

# หา keytool (อยู่ใน JDK)
where keytool

# สร้าง keystore
keytool -genkeypair -v ^
  -keystore smschecker-release.keystore ^
  -alias smschecker-release ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -storepass android123 ^
  -keypass android123 ^
  -dname "CN=SMS Checker, OU=ThaiPrompt, O=ThaiPrompt, L=Bangkok, ST=Bangkok, C=TH"
```

หรือใช้สคริปต์:
```bash
bash create-keystore.sh
```

---

## 📝 ขั้นตอนที่ 2: เพิ่ม Keystore ใน .gitignore

```bash
cd D:\Code\SmsChecker
echo "*.keystore" >> .gitignore
echo "keystore.properties" >> .gitignore
git add .gitignore
git commit -m "chore: add keystore to gitignore"
```

⚠️ **อย่า commit keystore ขึ้น GitHub!**

---

## 📝 ขั้นตอนที่ 3: สร้าง keystore.properties (สำหรับ local build)

สร้างไฟล์ `D:\Code\SmsChecker\keystore.properties`:

```properties
storeFile=smschecker-release.keystore
storePassword=android123
keyAlias=smschecker-release
keyPassword=android123
```

---

## 📝 ขั้นตอนที่ 4: อัพเดท build.gradle.kts

แก้ไข `app/build.gradle.kts` ให้อ่านค่าจาก `keystore.properties`:

```kotlin
// อ่านค่าจาก keystore.properties (local) หรือ environment variables (CI)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
}

android {
    // ...

    signingConfigs {
        create("release") {
            // Local: อ่านจาก keystore.properties
            // CI: อ่านจาก environment variables
            storeFile = file(
                keystoreProperties.getProperty("storeFile")
                    ?: System.getenv("SIGNING_STORE_FILE")
                    ?: "smschecker-release.keystore"
            )
            storePassword = keystoreProperties.getProperty("storePassword")
                ?: System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = keystoreProperties.getProperty("keyAlias")
                ?: System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = keystoreProperties.getProperty("keyPassword")
                ?: System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            // ...
        }
    }
}
```

---

## 📝 ขั้นตอนที่ 5: เพิ่ม Keystore ใน GitHub Secrets

1. เข้า GitHub: https://github.com/xjanova/smschecker/settings/secrets/actions
2. คลิก **New repository secret**
3. เพิ่ม secrets ต่อไปนี้:

### 5.1 แปลง Keystore เป็น Base64

```bash
# Windows PowerShell
cd D:\Code\SmsChecker
[Convert]::ToBase64String([IO.File]::ReadAllBytes("smschecker-release.keystore")) | Out-File keystore-base64.txt
```

หรือ

```bash
# Git Bash / WSL
cd /d/Code/SmsChecker
base64 smschecker-release.keystore > keystore-base64.txt
```

### 5.2 เพิ่ม Secrets

| Secret Name | Value |
|------------|-------|
| `SIGNING_KEYSTORE_BASE64` | เนื้อหาในไฟล์ `keystore-base64.txt` |
| `SIGNING_STORE_PASSWORD` | `android123` |
| `SIGNING_KEY_ALIAS` | `smschecker-release` |
| `SIGNING_KEY_PASSWORD` | `android123` |

---

## 📝 ขั้นตอนที่ 6: อัพเดท GitHub Actions Workflow

แก้ไข `.github/workflows/android-build.yml`:

```yaml
- name: Decode Keystore
  run: |
    echo "${{ secrets.SIGNING_KEYSTORE_BASE64 }}" | base64 -d > smschecker-release.keystore

- name: Build Release APK
  env:
    SIGNING_STORE_FILE: smschecker-release.keystore
    SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
    SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
    SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
  run: ./gradlew assembleRelease
```

---

## ✅ ผลลัพธ์

หลังจากตั้งค่าเสร็จ:

1. ✅ **Build Local** → ใช้ `keystore.properties`
2. ✅ **Build CI** → ใช้ GitHub Secrets
3. ✅ **Keystore เดียวกันตลอด** → อัพเดทได้โดยไม่ต้องถอนแอพ
4. ✅ **ข้อมูลไม่หาย** → Database, SharedPreferences ยังอยู่

---

## 🔐 ความปลอดภัย

⚠️ **สำคัญมาก:**
- **อย่า commit keystore ขึ้น Git**
- **อย่าแชร์ keystore ให้ใคร**
- **สำรองไว้หลายที่** (USB, Cloud ส่วนตัว)
- **ถ้าหาย → ไม่สามารถอัพเดทแอพได้อีก** ต้องเผยแพร่เป็น package name ใหม่

---

## 📊 ตรวจสอบ Keystore

```bash
# ดูข้อมูล keystore
keytool -list -v -keystore smschecker-release.keystore -storepass android123

# ตรวจสอบ APK ว่าใช้ keystore ไหน
keytool -printcert -jarfile app-release.apk
```

---

## 🆘 Troubleshooting

### ปัญหา: ติดตั้ง APK ไม่ได้ (INSTALL_FAILED_UPDATE_INCOMPATIBLE)

**สาเหตุ:** Signature ไม่ตรงกับเวอร์ชันเก่า

**วิธีแก้:**
1. ถอนแอพเวอร์ชันเก่า
2. ติดตั้งเวอร์ชันใหม่ที่ลงนามด้วย release keystore
3. จากนี้อัพเดทได้ปกติ

### ปัญหา: keytool: command not found

**วิธีแก้:**
- ใช้ Android Studio → Build → Generate Signed Bundle / APK แทน
- หรือหาที่อยู่ของ keytool: `C:\Program Files\Java\jdk-XX\bin\keytool.exe`

---

## 📚 อ้างอิง

- [Android Developer - Sign your app](https://developer.android.com/studio/publish/app-signing)
- [Android Developer - Configure signing](https://developer.android.com/build/building-cmdline#sign_cmdline)
