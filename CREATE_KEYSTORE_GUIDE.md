# 🔑 วิธีสร้าง Release Keystore สำหรับ SMS Checker

## 🎯 เป้าหมาย
สร้าง keystore เพื่อให้แอพอัพเดทได้โดยไม่ต้องถอน และข้อมูลไม่หาย

---

## ⚡ วิธีที่ 1: ใช้ Android Studio (แนะนำ - ง่ายที่สุด)

### ขั้นตอน:

1. **เปิดโปรเจค SMS Checker ใน Android Studio**

2. **เข้าเมนู Build**
   - คลิก **Build** → **Generate Signed Bundle / APK...**

3. **เลือก APK**
   - เลือก **APK**
   - คลิก **Next**

4. **สร้าง Key Store ใหม่**
   - คลิก **Create new...** (ข้างๆ Key store path)

5. **กรอกข้อมูล Key Store**
   ```
   📁 Key store path: D:\Code\SmsChecker\smschecker-release.keystore
   🔒 Password: android123
   🔒 Confirm: android123
   ```

6. **กรอกข้อมูล Key**
   ```
   🔑 Alias: smschecker-release
   🔒 Password: android123
   🔒 Confirm: android123
   ⏰ Validity (years): 27 (หรือมากกว่า)
   ```

7. **กรอกข้อมูล Certificate**
   ```
   👤 First and Last Name: SMS Checker
   🏢 Organizational Unit: ThaiPrompt
   🏢 Organization: ThaiPrompt
   🏙️ City or Locality: Bangkok
   🗺️ State or Province: Bangkok
   🌍 Country Code (XX): TH
   ```

8. **บันทึก**
   - คลิก **OK**
   - คลิก **Next**
   - เลือก **release**
   - คลิก **Finish**

9. **✅ เสร็จแล้ว!** ไฟล์ `smschecker-release.keystore` จะถูกสร้างที่ `D:\Code\SmsChecker\`

---

## ⚡ วิธีที่ 2: ใช้ Command Line (สำหรับผู้เชี่ยวชาญ)

### สำหรับ Windows (PowerShell หรือ CMD):

```powershell
# เข้าโฟลเดอร์โปรเจค
cd D:\Code\SmsChecker

# หา keytool (อยู่ใน JDK)
# ตัวอย่าง: C:\Program Files\Java\jdk-17\bin\keytool.exe
where.exe keytool

# สร้าง keystore (แทน PATH_TO_KEYTOOL ด้วยเส้นทางที่เจอ)
"C:\Program Files\Java\jdk-17\bin\keytool.exe" -genkeypair -v ^
  -keystore smschecker-release.keystore ^
  -alias smschecker-release ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -storepass android123 ^
  -keypass android123 ^
  -dname "CN=SMS Checker, OU=ThaiPrompt, O=ThaiPrompt, L=Bangkok, ST=Bangkok, C=TH"
```

### สำหรับ Mac/Linux:

```bash
cd /path/to/SmsChecker

keytool -genkeypair -v \
  -keystore smschecker-release.keystore \
  -alias smschecker-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass android123 \
  -keypass android123 \
  -dname "CN=SMS Checker, OU=ThaiPrompt, O=ThaiPrompt, L=Bangkok, ST=Bangkok, C=TH"
```

---

## 📝 ขั้นตอนหลังสร้าง Keystore

### 1. สร้างไฟล์ keystore.properties

สร้างไฟล์ `D:\Code\SmsChecker\keystore.properties` (ในโฟลเดอร์โปรเจค):

```properties
storeFile=smschecker-release.keystore
storePassword=android123
keyAlias=smschecker-release
keyPassword=android123
```

### 2. ตรวจสอบ .gitignore

ตรวจสอบว่า `.gitignore` มีบรรทัดนี้แล้ว:

```
*.keystore
keystore.properties
keystore-base64.txt
```

### 3. แปลง Keystore เป็น Base64 (สำหรับ GitHub Actions)

**Windows PowerShell:**
```powershell
cd D:\Code\SmsChecker
[Convert]::ToBase64String([IO.File]::ReadAllBytes("smschecker-release.keystore")) | Out-File keystore-base64.txt
```

**Git Bash / Mac / Linux:**
```bash
cd /path/to/SmsChecker
base64 smschecker-release.keystore > keystore-base64.txt
```

### 4. เพิ่ม GitHub Secrets

1. เข้า: https://github.com/xjanova/smschecker/settings/secrets/actions
2. คลิก **New repository secret**
3. เพิ่ม secrets ทั้ง 4 ตัว:

| Secret Name | Value | คำอธิบาย |
|------------|-------|---------|
| `SIGNING_KEYSTORE_BASE64` | เนื้อหาใน `keystore-base64.txt` | Keystore ที่แปลงเป็น Base64 |
| `SIGNING_STORE_PASSWORD` | `android123` | รหัส keystore |
| `SIGNING_KEY_ALIAS` | `smschecker-release` | ชื่อ key alias |
| `SIGNING_KEY_PASSWORD` | `android123` | รหัส key |

### 5. ทดสอบ Build Local

```bash
cd D:\Code\SmsChecker
./gradlew assembleRelease
```

ถ้าสำเร็จ จะได้ APK ที่: `app/build/outputs/apk/release/app-release.apk`

---

## ✅ ตรวจสอบว่า Keystore ถูกต้อง

### ดูข้อมูล Keystore:
```bash
keytool -list -v -keystore smschecker-release.keystore -storepass android123
```

ผลลัพธ์ควรแสดง:
```
Alias name: smschecker-release
Creation date: ...
Entry type: PrivateKeyEntry
Certificate chain length: 1
Certificate[1]:
Owner: CN=SMS Checker, OU=ThaiPrompt, O=ThaiPrompt, L=Bangkok, ST=Bangkok, C=TH
Issuer: CN=SMS Checker, OU=ThaiPrompt, O=ThaiPrompt, L=Bangkok, ST=Bangkok, C=TH
...
```

### ตรวจสอบ Signature ของ APK:
```bash
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

---

## 🔐 ความปลอดภัย

### ⚠️ สิ่งที่ต้องทำ:
- ✅ **สำรอง keystore ไว้หลายที่** (USB, Cloud ส่วนตัว, External HDD)
- ✅ **เก็บรหัสผ่านไว้ในที่ปลอดภัย** (Password Manager)
- ✅ **ห้ามแชร์ให้ใคร** (รวมทั้ง Git, Slack, Email)

### ❌ สิ่งที่ห้ามทำ:
- ❌ **ห้าม commit keystore ขึ้น Git** (เช็คใน .gitignore แล้ว)
- ❌ **ห้ามแชร์รหัสผ่านในที่สาธารณะ**
- ❌ **ห้ามลบ keystore** (ลบแล้วอัพเดทแอพไม่ได้)

### 💀 ถ้าหาย keystore จะเกิดอะไร?
- 😱 **ไม่สามารถอัพเดทแอพได้อีก**
- 😱 **ต้องเผยแพร่เป็นแอพใหม่** (package name ใหม่)
- 😱 **ผู้ใช้ต้องถอนแอพเก่า → ข้อมูลหาย**

---

## 🆘 Troubleshooting

### ปัญหา 1: keytool: command not found

**วิธีแก้:**
1. ใช้ Android Studio แทน (วิธีที่ 1)
2. หรือหาเส้นทาง keytool:
   - Windows: `C:\Program Files\Java\jdk-XX\bin\keytool.exe`
   - Mac: `/Library/Java/JavaVirtualMachines/jdk-XX.jdk/Contents/Home/bin/keytool`

### ปัญหา 2: ติดตั้ง APK ไม่ได้ (INSTALL_FAILED_UPDATE_INCOMPATIBLE)

**สาเหตุ:** Signature ไม่ตรงกับเวอร์ชันเก่า

**วิธีแก้:**
1. ถอนแอพเวอร์ชันเก่า (ที่ลงนามด้วย debug key)
2. ติดตั้งเวอร์ชันใหม่ (ที่ลงนามด้วย release keystore)
3. จากนี้ไปอัพเดทได้ปกติ ✅

### ปัญหา 3: Build failed - Keystore not found

**วิธีแก้:**
1. ตรวจสอบว่า `keystore.properties` อยู่ในโฟลเดอร์โปรเจค
2. ตรวจสอบว่า `smschecker-release.keystore` มีอยู่จริง
3. ตรวจสอบเส้นทางใน `keystore.properties` ถูกต้อง

---

## 📊 สรุป

หลังจากทำตามขั้นตอนนี้แล้ว:

1. ✅ มี `smschecker-release.keystore` สำหรับลงนาม
2. ✅ มี `keystore.properties` สำหรับ local build
3. ✅ มี GitHub Secrets สำหรับ CI build
4. ✅ Release APK สามารถอัพเดทได้โดยไม่ต้องถอนแอพ
5. ✅ ข้อมูล (Database, SharedPreferences) ไม่หายหลังอัพเดท

---

## 📚 เอกสารเพิ่มเติม

- [Android - Sign your app](https://developer.android.com/studio/publish/app-signing)
- [SIGNING_SETUP.md](./SIGNING_SETUP.md) - เอกสารแนะนำแบบละเอียด
