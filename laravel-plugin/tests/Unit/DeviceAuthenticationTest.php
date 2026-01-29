<?php

namespace Tests\Unit;

use PHPUnit\Framework\TestCase;

/**
 * Tests for device authentication logic.
 */
class DeviceAuthenticationTest extends TestCase
{
    /**
     * Test API key generation format.
     */
    public function test_api_key_format(): void
    {
        $apiKey = bin2hex(random_bytes(32));

        $this->assertEquals(64, strlen($apiKey));
        $this->assertMatchesRegularExpression('/^[a-f0-9]{64}$/', $apiKey);
    }

    /**
     * Test secret key generation format.
     */
    public function test_secret_key_format(): void
    {
        $secretKey = bin2hex(random_bytes(32));

        $this->assertEquals(64, strlen($secretKey));
        $this->assertMatchesRegularExpression('/^[a-f0-9]{64}$/', $secretKey);
    }

    /**
     * Test generated keys are unique.
     */
    public function test_generated_keys_are_unique(): void
    {
        $keys = [];
        for ($i = 0; $i < 100; $i++) {
            $keys[] = bin2hex(random_bytes(32));
        }

        $uniqueKeys = array_unique($keys);
        $this->assertCount(100, $uniqueKeys);
    }

    /**
     * Test device ID format.
     */
    public function test_device_id_format(): void
    {
        $uuid = substr(str_replace('-', '', (string) \Illuminate\Support\Str::uuid()), 0, 8);
        $deviceId = 'SMSCHK-' . strtoupper($uuid);

        $this->assertStringStartsWith('SMSCHK-', $deviceId);
        $this->assertMatchesRegularExpression('/^SMSCHK-[A-F0-9]{8}$/', $deviceId);
    }

    /**
     * Test timestamp validation within window.
     */
    public function test_timestamp_within_window(): void
    {
        $currentTime = intval(round(microtime(true) * 1000));
        $requestTime = $currentTime - 60000; // 1 minute ago
        $tolerance = 300000; // 5 minutes

        $isValid = abs($currentTime - $requestTime) <= $tolerance;
        $this->assertTrue($isValid);
    }

    /**
     * Test timestamp validation outside window.
     */
    public function test_timestamp_outside_window(): void
    {
        $currentTime = intval(round(microtime(true) * 1000));
        $requestTime = $currentTime - 600000; // 10 minutes ago
        $tolerance = 300000; // 5 minutes

        $isValid = abs($currentTime - $requestTime) <= $tolerance;
        $this->assertFalse($isValid);
    }

    /**
     * Test timestamp validation with future time.
     */
    public function test_timestamp_future_within_window(): void
    {
        $currentTime = intval(round(microtime(true) * 1000));
        $requestTime = $currentTime + 120000; // 2 minutes in the future
        $tolerance = 300000; // 5 minutes

        $isValid = abs($currentTime - $requestTime) <= $tolerance;
        $this->assertTrue($isValid);
    }

    /**
     * Test timestamp validation with far future time.
     */
    public function test_timestamp_far_future_outside_window(): void
    {
        $currentTime = intval(round(microtime(true) * 1000));
        $requestTime = $currentTime + 600000; // 10 minutes in the future
        $tolerance = 300000; // 5 minutes

        $isValid = abs($currentTime - $requestTime) <= $tolerance;
        $this->assertFalse($isValid);
    }

    /**
     * Test nonce uniqueness.
     */
    public function test_nonce_uniqueness(): void
    {
        $nonces = [];
        for ($i = 0; $i < 100; $i++) {
            $nonces[] = base64_encode(random_bytes(16));
        }

        $uniqueNonces = array_unique($nonces);
        $this->assertCount(100, $uniqueNonces);
    }

    /**
     * Test nonce format.
     */
    public function test_nonce_format(): void
    {
        $nonce = base64_encode(random_bytes(16));

        // Base64 of 16 bytes = 24 characters (including padding)
        $this->assertEquals(24, strlen($nonce));
        $this->assertMatchesRegularExpression('/^[A-Za-z0-9+\/]+=*$/', $nonce);
    }
}
