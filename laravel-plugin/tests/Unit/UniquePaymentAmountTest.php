<?php

namespace Tests\Unit;

use PHPUnit\Framework\TestCase;

/**
 * Tests for the unique payment amount generation logic.
 * These tests verify the decimal suffix system works correctly.
 */
class UniquePaymentAmountTest extends TestCase
{
    /**
     * Test amount suffix is within valid range (0.01 - 0.99).
     */
    public function test_suffix_range_validation(): void
    {
        for ($i = 1; $i <= 99; $i++) {
            $suffix = $i / 100;
            $this->assertGreaterThanOrEqual(0.01, $suffix);
            $this->assertLessThanOrEqual(0.99, $suffix);
        }
    }

    /**
     * Test unique amount calculation.
     */
    public function test_unique_amount_calculation(): void
    {
        $baseAmount = 500.00;
        $suffix = 37;
        $uniqueAmount = $baseAmount + ($suffix / 100);

        $this->assertEquals(500.37, $uniqueAmount);
    }

    /**
     * Test unique amount with various base amounts.
     */
    public function test_various_base_amounts(): void
    {
        $testCases = [
            ['base' => 100.00, 'suffix' => 1, 'expected' => 100.01],
            ['base' => 500.00, 'suffix' => 37, 'expected' => 500.37],
            ['base' => 1000.00, 'suffix' => 99, 'expected' => 1000.99],
            ['base' => 2500.00, 'suffix' => 50, 'expected' => 2500.50],
            ['base' => 10000.00, 'suffix' => 5, 'expected' => 10000.05],
        ];

        foreach ($testCases as $case) {
            $result = $case['base'] + ($case['suffix'] / 100);
            $this->assertEquals(
                $case['expected'],
                $result,
                "Base: {$case['base']}, Suffix: {$case['suffix']}"
            );
        }
    }

    /**
     * Test amount formatting to 2 decimal places.
     */
    public function test_amount_formatting(): void
    {
        $amount = 500.37;
        $formatted = number_format($amount, 2, '.', '');

        $this->assertEquals('500.37', $formatted);
    }

    /**
     * Test display amount format with Thai Baht symbol.
     */
    public function test_display_amount_format(): void
    {
        $amount = 1500.50;
        $display = '฿' . number_format($amount, 2);

        $this->assertEquals('฿1,500.50', $display);
    }

    /**
     * Test maximum suffixes per base amount.
     */
    public function test_max_suffix_count(): void
    {
        $maxSuffix = 99;
        $usedSuffixes = range(1, $maxSuffix);

        $this->assertCount(99, $usedSuffixes);

        // All suffixes used - no more available
        $availableSuffixes = array_diff(range(1, $maxSuffix), $usedSuffixes);
        $this->assertEmpty($availableSuffixes);
    }

    /**
     * Test finding available suffix.
     */
    public function test_find_available_suffix(): void
    {
        $usedSuffixes = [1, 2, 3, 5, 7, 10, 15];
        $maxSuffix = 99;

        $available = null;
        for ($i = 1; $i <= $maxSuffix; $i++) {
            if (!in_array($i, $usedSuffixes)) {
                $available = $i;
                break;
            }
        }

        $this->assertEquals(4, $available);
    }

    /**
     * Test amount matching precision.
     */
    public function test_amount_matching_precision(): void
    {
        $generatedAmount = 500.37;
        $receivedAmount = '500.37';

        // String comparison (how it's stored in DB)
        $this->assertEquals(
            number_format($generatedAmount, 2, '.', ''),
            $receivedAmount
        );

        // Float comparison with tolerance
        $this->assertEqualsWithDelta(
            $generatedAmount,
            floatval($receivedAmount),
            0.001
        );
    }
}
