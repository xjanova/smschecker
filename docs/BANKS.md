# Supported Thai Banks & SMS Patterns

This document describes the SMS parsing patterns for each supported Thai bank.

---

## Supported Banks

| Code | Bank Name (EN) | Bank Name (TH) | SMS Senders |
|------|----------------|-----------------|-------------|
| `KBANK` | Kasikorn Bank | ธนาคารกสิกรไทย | KBANK, KBank, K-Bank, KPlus |
| `SCB` | Siam Commercial Bank | ธนาคารไทยพาณิชย์ | SCB, SCBeasy, SCBEasy, SCBEASY |
| `KTB` | Krungthai Bank | ธนาคารกรุงไทย | KTB, Krungthai, KTB-BANK |
| `BBL` | Bangkok Bank | ธนาคารกรุงเทพ | BBL, Bangkok Bank, BualuangiBanking |
| `GSB` | Government Savings Bank | ธนาคารออมสิน | GSB, MyMo, MYMO |
| `BAY` | Bank of Ayudhya (Krungsri) | ธนาคารกรุงศรีอยุธยา | BAY, KMA, Krungsri |
| `TTB` | TMBThanachart Bank | ธนาคารทีเอ็มบีธนชาต | TTB, ttb, TMB, Thanachart, ttbbank |
| `PROMPTPAY` | PromptPay | พร้อมเพย์ | PromptPay, PROMPTPAY |

---

## SMS Pattern Recognition

### Credit (Money In) Keywords

The parser recognizes the following keywords as **incoming** transactions:

| Language | Keywords |
|----------|----------|
| Thai | รับโอน, เงินเข้า, โอนเข้า |
| English | Received, Transfer In, CR, credit, Deposit |
| PromptPay | PromptPay รับ |

### Debit (Money Out) Keywords

The parser recognizes the following keywords as **outgoing** transactions:

| Language | Keywords |
|----------|----------|
| Thai | โอนออก, โอนเงิน, จ่าย, ชำระ, ถอน, หัก |
| English | Transfer Out, DR, debit, Payment, Paid, Withdraw, Purchase |

---

## Amount Patterns

The parser supports multiple amount formats:

```
THB 500.37          → 500.37
฿500.37             → 500.37
500.37 บาท          → 500.37
บ. 500.37           → 500.37
1,500.00            → 1500.00
500                 → 500.00
```

### Amount Normalization

All amounts are normalized to 2 decimal places:
- `500` → `500.00`
- `500.3` → `500.30`
- `1,234.56` → `1234.56`

---

## Bank-Specific SMS Examples

### KBANK (Kasikorn Bank)

**Credit:**
```
KBANK: รับโอน 500.37 บ. บช xxx1234 จาก นายทดสอบ Ref.ABC123 29/01 14:30
```
- Amount: `500.37`
- Type: `credit`
- Account: `1234`
- Sender: `นายทดสอบ`
- Reference: `ABC123`

**Debit:**
```
KBANK: โอนออก 1,000.00 บ. บช xxx5678 ไปยัง นายรับเงิน Ref.XYZ789 29/01 15:00
```
- Amount: `1000.00`
- Type: `debit`
- Account: `5678`
- Receiver: `นายรับเงิน`
- Reference: `XYZ789`

### SCB (Siam Commercial Bank)

**Credit:**
```
SCB: เงินเข้า THB 2,500.50 บช.xxx9012 จาก บริษัท ทดสอบ จำกัด Ref No.REF001
```

**Debit:**
```
SCB: ชำระเงิน THB 800.00 บช.xxx9012 ไปยัง ร้านค้าทดสอบ Ref No.PAY001
```

### KTB (Krungthai Bank)

**Credit:**
```
KTB: Transfer In THB 3,000.25 A/C xxx3456 From นายส่งเงิน Ref.TFR001
```

**Debit:**
```
KTB: Payment THB 1,500.00 A/C xxx3456 To ร้านค้า Ref.PAY002
```

### BBL (Bangkok Bank)

**Credit:**
```
BBL: Received ฿5,000.00 Acct xxx7890 From บริษัท ABC Ref.RCV001
```

**Debit:**
```
BBL: Transfer Out ฿2,000.00 Acct xxx7890 To นายรับ Ref.TFR002
```

### GSB (Government Savings Bank)

**Credit:**
```
MyMo: รับโอน 1,000.00 บาท บัญชี xxx2345 จาก นายโอน อ้างอิง GSB001
```

**Debit:**
```
MyMo: โอนเงิน 500.00 บาท บัญชี xxx2345 ถึง นายรับ อ้างอิง GSB002
```

### BAY (Krungsri / Bank of Ayudhya)

**Credit:**
```
Krungsri: เงินเข้า ฿750.00 บช xxx6789 จาก นายส่ง Ref KMA001
```

**Debit:**
```
KMA: จ่าย ฿1,200.00 บช xxx6789 ไปยัง ร้านค้า Ref KMA002
```

### TTB (TMBThanachart Bank)

**Credit:**
```
ttb: CR THB 900.00 A/C *1234 From นายทดสอบ Ref TTB001
```

**Debit:**
```
ttb: DR THB 450.00 A/C *1234 Payment ร้านค้า Ref TTB002
```

### PromptPay

**Credit:**
```
PromptPay: PromptPay รับ THB 300.00 จาก 0812345xxxx Ref PP001
```

---

## Account Number Extraction

The parser identifies account numbers from various formats:

| Pattern | Example | Extracted |
|---------|---------|-----------|
| `บช xxx1234` | บช xxx1234 | `1234` |
| `A/C xxx5678` | A/C xxx5678 | `5678` |
| `Acct *9012` | Acct *9012 | `9012` |
| `บัญชี xxx3456` | บัญชี xxx3456 | `3456` |
| `XXX-X-XXXXX-X` | 123-4-56789-0 | Last 4 digits |

---

## Reference Number Extraction

| Pattern | Example | Extracted |
|---------|---------|-----------|
| `Ref.ABC123` | Ref.ABC123 | `ABC123` |
| `Ref No.XYZ` | Ref No.XYZ789 | `XYZ789` |
| `อ้างอิง GSB001` | อ้างอิง GSB001 | `GSB001` |
| `รหัส PAY001` | รหัส PAY001 | `PAY001` |
| `เลขที่ TXN001` | เลขที่ TXN001 | `TXN001` |

---

## Name Extraction

| Pattern | Direction | Example |
|---------|-----------|---------|
| `จาก {name}` | Sender | จาก นายทดสอบ |
| `from {name}` | Sender | From John Doe |
| `ผู้โอน {name}` | Sender | ผู้โอน บริษัท ABC |
| `ไปยัง {name}` | Receiver | ไปยัง นายรับเงิน |
| `ถึง {name}` | Receiver | ถึง ร้านค้าทดสอบ |
| `to {name}` | Receiver | To Shop ABC |
| `ผู้รับ {name}` | Receiver | ผู้รับ นายสมชาย |

---

## Adding a New Bank

To add support for a new bank, modify `BankSmsParser.kt`:

### 1. Add Bank Sender

```kotlin
// In BANK_SENDERS map
"NEWBANK" to listOf("NEWBANK", "NewBankApp", "NewBank-SMS"),
```

### 2. Test SMS Patterns

Before adding, collect real SMS samples and verify:
- Credit keywords used
- Debit keywords used
- Amount format
- Account number format
- Reference number format

### 3. Add Specific Patterns (if needed)

If the bank uses unique patterns not covered by existing regex:

```kotlin
// Add to CREDIT_PATTERNS or DEBIT_PATTERNS
Regex("""NewBank specific pattern (\d+\.\d{2})""", RegexOption.IGNORE_CASE),
```

### 4. Update Documentation

Add the new bank to:
- This document (`docs/BANKS.md`)
- `README.md` bank table
- `config/smschecker.php` supported_banks array

### 5. Add Unit Tests

```kotlin
@Test
fun `parse NEWBANK credit SMS`() {
    val result = parser.parse(
        "NEWBANK",
        "NEWBANK: เงินเข้า 500.00 บ. บช xxx1234",
        System.currentTimeMillis()
    )
    assertNotNull(result)
    assertEquals("NEWBANK", result?.bank)
    assertEquals(TransactionType.CREDIT, result?.type)
    assertEquals("500.00", result?.amount)
}
```

---

## Troubleshooting

### SMS Not Detected

1. **Check sender address**: The SMS sender must match one of the known senders in `BANK_SENDERS`
2. **Check keywords**: The message must contain at least one credit or debit keyword
3. **Check amount format**: Amount must match the regex patterns (digits with optional commas and decimals)

### Wrong Transaction Type

If a credit is detected as debit (or vice versa):
- The parser checks for **both** credit and debit keywords
- If both match, it uses **keyword position** (earliest keyword wins)
- Report the SMS sample so patterns can be refined

### Amount Parsing Error

- Ensure the amount uses standard digits (not Thai numerals)
- Check for unusual separators (some banks use spaces in amounts)
- Amounts must be positive and greater than 0.01

### Known Limitations

- **Thai numerals** (๐-๙) are not supported (banks use Arabic numerals in SMS)
- **Multi-language SMS**: Some banks send mixed Thai/English; the parser handles this
- **Promotional SMS**: Bank marketing messages may be falsely detected if they contain transaction keywords with amounts
- **SMS length**: Multi-part SMS messages are automatically concatenated by the Android receiver before parsing
