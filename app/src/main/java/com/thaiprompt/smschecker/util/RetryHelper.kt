package com.thaiprompt.smschecker.util

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Helper class for retrying network operations with exponential backoff
 * ใช้สำหรับลองทำงานซ้ำเมื่อเน็ตไม่เสถียร
 */
object RetryHelper {

    private const val TAG = "RetryHelper"

    // Default retry configuration
    private const val DEFAULT_MAX_RETRIES = 5
    private const val DEFAULT_INITIAL_DELAY_MS = 1000L
    private const val DEFAULT_MAX_DELAY_MS = 16000L
    private const val DEFAULT_BACKOFF_MULTIPLIER = 2.0

    /**
     * Execute a suspend function with retry logic
     * @param maxRetries Maximum number of retry attempts (default: 5)
     * @param initialDelayMs Initial delay in milliseconds before first retry (default: 1000ms)
     * @param maxDelayMs Maximum delay between retries (default: 16000ms)
     * @param backoffMultiplier Multiplier for exponential backoff (default: 2.0)
     * @param block The suspend function to execute
     * @return Result of the operation or null if all retries failed
     */
    suspend fun <T> withRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        block: suspend () -> T
    ): T? {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e

                // Check if this is a retryable error
                if (!isRetryableException(e)) {
                    Log.w(TAG, "Non-retryable exception encountered: ${e.message}")
                    return null
                }

                if (attempt < maxRetries) {
                    Log.d(TAG, "Attempt ${attempt + 1} failed: ${e.message}. Retrying in ${currentDelay}ms...")
                    delay(currentDelay)
                    currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
                } else {
                    Log.e(TAG, "All $maxRetries retry attempts failed", e)
                }
            }
        }

        return null
    }

    /**
     * Execute a suspend function that returns Boolean with retry logic
     * @return true if operation succeeded, false if all retries failed
     */
    suspend fun withRetryBoolean(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        block: suspend () -> Boolean
    ): Boolean {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                val result = block()
                if (result) {
                    if (attempt > 0) {
                        Log.d(TAG, "Operation succeeded on attempt ${attempt + 1}")
                    }
                    return true
                }

                // Operation returned false, but no exception
                if (attempt < maxRetries) {
                    Log.d(TAG, "Attempt ${attempt + 1} returned false. Retrying in ${currentDelay}ms...")
                    delay(currentDelay)
                    currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
                }
            } catch (e: Exception) {
                lastException = e

                // Check if this is a retryable error
                if (!isRetryableException(e)) {
                    Log.w(TAG, "Non-retryable exception encountered: ${e.message}")
                    return false
                }

                if (attempt < maxRetries) {
                    Log.d(TAG, "Attempt ${attempt + 1} failed: ${e.message}. Retrying in ${currentDelay}ms...")
                    delay(currentDelay)
                    currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
                } else {
                    Log.e(TAG, "All $maxRetries retry attempts failed", e)
                }
            }
        }

        return false
    }

    /**
     * Check if an exception is retryable (network-related)
     */
    private fun isRetryableException(exception: Exception): Boolean {
        return when (exception) {
            is IOException,
            is SocketException,
            is SocketTimeoutException,
            is UnknownHostException,
            is SSLException -> true
            else -> {
                // Check if the exception message contains network-related keywords
                val message = exception.message?.lowercase() ?: ""
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("network") ||
                message.contains("unreachable") ||
                message.contains("socket")
            }
        }
    }
}
