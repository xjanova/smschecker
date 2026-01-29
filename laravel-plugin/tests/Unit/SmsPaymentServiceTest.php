<?php

namespace Tests\Unit;

use PHPUnit\Framework\TestCase;
use App\Services\SmsPaymentService;

class SmsPaymentServiceTest extends TestCase
{
    private SmsPaymentService $service;

    protected function setUp(): void
    {
        parent::setUp();
        $this->service = new SmsPaymentService();
    }

    /**
     * Test HMAC signature generation and verification.
     */
    public function test_verify_signature_with_valid_data(): void
    {
        $secretKey = 'test_secret_key_1234567890abcdef';
        $data = 'encrypted_payload_data' . 'nonce123' . '1706540000000';

        $signature = base64_encode(hash_hmac('sha256', $data, $secretKey, true));

        $this->assertTrue($this->service->verifySignature($data, $signature, $secretKey));
    }

    /**
     * Test HMAC signature fails with wrong key.
     */
    public function test_verify_signature_with_wrong_key(): void
    {
        $secretKey = 'test_secret_key_1234567890abcdef';
        $wrongKey = 'wrong_key_1234567890abcdef';
        $data = 'encrypted_payload_data' . 'nonce123' . '1706540000000';

        $signature = base64_encode(hash_hmac('sha256', $data, $secretKey, true));

        $this->assertFalse($this->service->verifySignature($data, $signature, $wrongKey));
    }

    /**
     * Test HMAC signature fails with tampered data.
     */
    public function test_verify_signature_with_tampered_data(): void
    {
        $secretKey = 'test_secret_key_1234567890abcdef';
        $data = 'original_data';
        $tamperedData = 'tampered_data';

        $signature = base64_encode(hash_hmac('sha256', $data, $secretKey, true));

        $this->assertFalse($this->service->verifySignature($tamperedData, $signature, $secretKey));
    }

    /**
     * Test HMAC signature fails with invalid base64.
     */
    public function test_verify_signature_with_invalid_signature(): void
    {
        $secretKey = 'test_secret_key_1234567890abcdef';
        $data = 'test_data';

        $this->assertFalse($this->service->verifySignature($data, 'invalid_base64_signature', $secretKey));
    }

    /**
     * Test AES-256-GCM encryption and decryption roundtrip.
     */
    public function test_encrypt_decrypt_roundtrip(): void
    {
        $secretKey = str_repeat('a', 64); // 64 hex chars
        $payload = [
            'bank' => 'KBANK',
            'type' => 'credit',
            'amount' => '500.37',
            'account_number' => '1234',
            'sender_or_receiver' => 'Test User',
            'reference_number' => 'REF001',
            'sms_timestamp' => 1706540000000,
            'device_id' => 'SMSCHK-TEST',
            'nonce' => 'test_nonce_123',
        ];

        $plainText = json_encode($payload);

        // Encrypt using the same algorithm
        $key = str_pad(substr($secretKey, 0, 32), 32, "\0");
        $iv = random_bytes(12);
        $tag = '';

        $cipherText = openssl_encrypt(
            $plainText,
            'aes-256-gcm',
            $key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            '',
            16
        );

        $combined = $iv . $cipherText . $tag;
        $encoded = base64_encode($combined);

        // Decrypt using service
        $result = $this->service->decryptPayload($encoded, $secretKey);

        $this->assertNotNull($result);
        $this->assertEquals('KBANK', $result['bank']);
        $this->assertEquals('credit', $result['type']);
        $this->assertEquals('500.37', $result['amount']);
        $this->assertEquals('1234', $result['account_number']);
        $this->assertEquals('Test User', $result['sender_or_receiver']);
        $this->assertEquals('REF001', $result['reference_number']);
    }

    /**
     * Test decryption fails with wrong key.
     */
    public function test_decrypt_with_wrong_key(): void
    {
        $correctKey = str_repeat('a', 64);
        $wrongKey = str_repeat('b', 64);
        $plainText = json_encode(['test' => 'data']);

        $key = str_pad(substr($correctKey, 0, 32), 32, "\0");
        $iv = random_bytes(12);
        $tag = '';

        $cipherText = openssl_encrypt(
            $plainText,
            'aes-256-gcm',
            $key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            '',
            16
        );

        $combined = $iv . $cipherText . $tag;
        $encoded = base64_encode($combined);

        $result = $this->service->decryptPayload($encoded, $wrongKey);

        $this->assertNull($result);
    }

    /**
     * Test decryption fails with invalid base64.
     */
    public function test_decrypt_with_invalid_base64(): void
    {
        $result = $this->service->decryptPayload('not-valid-base64!!!', 'secret');

        $this->assertNull($result);
    }

    /**
     * Test decryption fails with too short data.
     */
    public function test_decrypt_with_too_short_data(): void
    {
        $shortData = base64_encode('short');

        $result = $this->service->decryptPayload($shortData, 'secret');

        $this->assertNull($result);
    }

    /**
     * Test decryption fails with non-JSON payload.
     */
    public function test_decrypt_with_non_json_payload(): void
    {
        $secretKey = str_repeat('a', 64);
        $plainText = 'this is not json';

        $key = str_pad(substr($secretKey, 0, 32), 32, "\0");
        $iv = random_bytes(12);
        $tag = '';

        $cipherText = openssl_encrypt(
            $plainText,
            'aes-256-gcm',
            $key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            '',
            16
        );

        $combined = $iv . $cipherText . $tag;
        $encoded = base64_encode($combined);

        $result = $this->service->decryptPayload($encoded, $secretKey);

        $this->assertNull($result);
    }

    /**
     * Test signature is different for different data.
     */
    public function test_different_data_produces_different_signatures(): void
    {
        $secretKey = 'test_secret_key';
        $data1 = 'payload1nonce11706540000000';
        $data2 = 'payload2nonce21706540000001';

        $sig1 = base64_encode(hash_hmac('sha256', $data1, $secretKey, true));
        $sig2 = base64_encode(hash_hmac('sha256', $data2, $secretKey, true));

        $this->assertNotEquals($sig1, $sig2);
        $this->assertTrue($this->service->verifySignature($data1, $sig1, $secretKey));
        $this->assertTrue($this->service->verifySignature($data2, $sig2, $secretKey));
    }

    /**
     * Test Thai characters in payload encryption/decryption.
     */
    public function test_encrypt_decrypt_with_thai_characters(): void
    {
        $secretKey = str_repeat('c', 64);
        $payload = [
            'bank' => 'KBANK',
            'type' => 'credit',
            'amount' => '1500.00',
            'sender_or_receiver' => 'นายทดสอบ ภาษาไทย',
            'reference_number' => 'REF-TH-001',
            'nonce' => 'thai_nonce',
        ];

        $plainText = json_encode($payload, JSON_UNESCAPED_UNICODE);

        $key = str_pad(substr($secretKey, 0, 32), 32, "\0");
        $iv = random_bytes(12);
        $tag = '';

        $cipherText = openssl_encrypt(
            $plainText,
            'aes-256-gcm',
            $key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            '',
            16
        );

        $combined = $iv . $cipherText . $tag;
        $encoded = base64_encode($combined);

        $result = $this->service->decryptPayload($encoded, $secretKey);

        $this->assertNotNull($result);
        $this->assertEquals('นายทดสอบ ภาษาไทย', $result['sender_or_receiver']);
    }
}
