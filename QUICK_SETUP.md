# ⚡ Quick Setup - Release Signing

## 🎯 เป้าหมาย
ให้ Release APK อัพเดทได้โดยไม่ต้องถอนแอพและข้อมูลไม่หาย

---

## 📋 ขั้นตอน (ทำตามลำดับ)

### ขั้นที่ 1: สร้าง Keystore ด้วย Android Studio

1. **เปิด Android Studio**
   - เปิดโปรเจค `D:\Code\SmsChecker`

2. **สร้าง Keystore**
   - เมนู **Build** → **Generate Signed Bundle / APK...**
   - เลือก **APK** → คลิก **Next**
   - คลิก **Create new...** (ข้าง Key store path)

3. **กรอกข้อมูล**

   **Key store path:**
   ```
   D:\Code\SmsChecker\smschecker-release.keystore
   ```

   **Password:**
   ```
   android123
   ```

   **Confirm:**
   ```
   android123
   ```

   **Alias:**
   ```
   smschecker-release
   ```

   **Password:**
   ```
   android123
   ```

   **Confirm:**
   ```
   android123
   ```

   **Validity (years):**
   ```
   27
   ```

   **Certificate:**
   ```
   First and Last Name: SMS Checker
   Organizational Unit: ThaiPrompt
   Organization: ThaiPrompt
   City or Locality: Bangkok
   State or Province: Bangkok
   Country Code: TH
   ```

4. **บันทึก**
   - คลิก **OK**
   - คลิก **Next**
   - เลือก **release**
   - คลิก **Finish**

5. **✅ เสร็จขั้นที่ 1** - จะได้ไฟล์ `smschecker-release.keystore`

---

### ขั้นที่ 2: สร้างไฟล์ keystore.properties

1. **สร้างไฟล์ใหม่**
   - ที่: `D:\Code\SmsChecker\keystore.properties`

2. **เขียนเนื้อหา:**
   ```properties
   storeFile=smschecker-release.keystore
   storePassword=android123
   keyAlias=smschecker-release
   keyPassword=android123
   ```

3. **บันทึกไฟล์**

---

### ขั้นที่ 3: แปลง Keystore เป็น Base64

**เปิด PowerShell:**
```powershell
cd D:\Code\SmsChecker
[Convert]::ToBase64String([IO.File]::ReadAllBytes("smschecker-release.keystore")) | Out-File keystore-base64.txt
```

จะได้ไฟล์ `keystore-base64.txt`

---

### ขั้นที่ 4: เพิ่ม GitHub Secrets

1. **เข้า GitHub:**
   https://github.com/xjanova/smschecker/settings/secrets/actions

2. **คลิก New repository secret**

3. **เพิ่ม Secret ที่ 1:**
   - Name: `SIGNING_KEYSTORE_BASE64`
   - Secret: **เปิดไฟล์ `keystore-base64.txt` → Copy ทั้งหมด → Paste**

4. **เพิ่ม Secret ที่ 2:**
   - Name: `SIGNING_STORE_PASSWORD`
   - Secret: `android123`

5. **เพิ่ม Secret ที่ 3:**
   - Name: `SIGNING_KEY_ALIAS`
   - Secret: `smschecker-release`

6. **เพิ่ม Secret ที่ 4:**
   - Name: `SIGNING_KEY_PASSWORD`
   - Secret: `android123`

---

### ขั้นที่ 5: Push และดู GitHub Actions Build

```bash
# Commit PowerShell script
cd D:\Code\SmsChecker
git add create-keystore.ps1 QUICK_SETUP.md
git commit -m "docs: add PowerShell keystore creation script"
git push origin main
```

**เข้าดู Build:**
https://github.com/xjanova/smschecker/actions

รอ build เสร็จ → Download APK → ติดตั้งทดสอบ

---

## ✅ ตรวจสอบความสำเร็จ

### ตรวจสอบ Keystore:
```bash
cd D:\Code\SmsChecker
ls smschecker-release.keystore
ls keystore.properties
ls keystore-base64.txt
```

### ตรวจสอบ GitHub Secrets:
https://github.com/xjanova/smschecker/settings/secrets/actions

ควรมี 4 secrets:
- ✅ SIGNING_KEYSTORE_BASE64
- ✅ SIGNING_STORE_PASSWORD
- ✅ SIGNING_KEY_ALIAS
- ✅ SIGNING_KEY_PASSWORD

### ทดสอบ Local Build (Optional):
```bash
cd D:\Code\SmsChecker
./gradlew assembleRelease
```

APK จะอยู่ที่: `app/build/outputs/apk/release/app-release.apk`

---

## 🎉 เสร็จสิ้น!

หลังจากนี้:
- ✅ Release APK สามารถอัพเดทได้โดยไม่ต้องถอนแอพ
- ✅ ข้อมูลผู้ใช้ไม่หาย (Database, SharedPreferences ยังอยู่)
- ✅ ทุกครั้งที่ push ไป main branch → GitHub Actions จะ build Release APK อัตโนมัติ

---

## ⚠️ สิ่งสำคัญ

**อย่าลืม:**
- 🔒 **ห้าม commit keystore ขึ้น Git** (มีใน .gitignore แล้ว)
- 💾 **สำรอง keystore ไว้หลายที่** (USB, Cloud ส่วนตัว)
- 🔐 **เก็บรหัสผ่านไว้ในที่ปลอดภัย**

**ถ้าหาย keystore:**
- 😱 ไม่สามารถอัพเดทแอพได้อีก
- 😱 ต้องเผยแพร่เป็นแอพใหม่ (package name ใหม่)
- 😱 ผู้ใช้ต้องถอนแอพเก่า → ข้อมูลหาย

---

## 📚 เอกสารเพิ่มเติม

- **CREATE_KEYSTORE_GUIDE.md** - คู่มือสร้าง keystore แบบละเอียด
- **SIGNING_SETUP.md** - เอกสารแนะนำ Release Signing ทั้งหมด
