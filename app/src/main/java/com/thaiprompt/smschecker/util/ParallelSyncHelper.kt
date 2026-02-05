package com.thaiprompt.smschecker.util

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Helper for parallel operations across multiple servers.
 *
 * Features:
 * - Parallel execution with configurable concurrency limit
 * - Individual timeouts per operation
 * - Graceful error handling (one failure doesn't stop others)
 * - Detailed result tracking per server
 */
object ParallelSyncHelper {

    private const val TAG = "ParallelSyncHelper"

    // Default configuration
    private const val DEFAULT_MAX_CONCURRENCY = 5
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    data class SyncResult<T>(
        val serverId: Long,
        val serverName: String,
        val success: Boolean,
        val data: T? = null,
        val error: String? = null,
        val durationMs: Long = 0
    )

    data class ParallelSyncResult<T>(
        val results: List<SyncResult<T>>,
        val totalDurationMs: Long,
        val successCount: Int,
        val failureCount: Int
    ) {
        val allSucceeded: Boolean get() = failureCount == 0
        val anySucceeded: Boolean get() = successCount > 0
    }

    /**
     * Execute an operation on multiple servers in parallel.
     *
     * @param servers List of server IDs and names to operate on
     * @param maxConcurrency Maximum number of concurrent operations
     * @param timeoutMs Timeout per operation in milliseconds
     * @param operation The suspend function to execute for each server
     * @return Aggregated results from all servers
     */
    suspend fun <T> executeParallel(
        servers: List<Pair<Long, String>>, // Pair<serverId, serverName>
        maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        operation: suspend (serverId: Long) -> T
    ): ParallelSyncResult<T> = coroutineScope {
        val startTime = System.currentTimeMillis()
        val semaphore = Semaphore(maxConcurrency)

        val results = servers.map { (serverId, serverName) ->
            async {
                semaphore.withPermit {
                    executeWithTimeout(serverId, serverName, timeoutMs, operation)
                }
            }
        }.awaitAll()

        val totalDuration = System.currentTimeMillis() - startTime
        val successCount = results.count { it.success }
        val failureCount = results.size - successCount

        Log.d(TAG, "Parallel sync completed: $successCount/${results.size} succeeded in ${totalDuration}ms")

        ParallelSyncResult(
            results = results,
            totalDurationMs = totalDuration,
            successCount = successCount,
            failureCount = failureCount
        )
    }

    private suspend fun <T> executeWithTimeout(
        serverId: Long,
        serverName: String,
        timeoutMs: Long,
        operation: suspend (serverId: Long) -> T
    ): SyncResult<T> {
        val startTime = System.currentTimeMillis()

        return try {
            val result = withTimeout(timeoutMs) {
                operation(serverId)
            }
            val duration = System.currentTimeMillis() - startTime

            SyncResult(
                serverId = serverId,
                serverName = serverName,
                success = true,
                data = result,
                durationMs = duration
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Operation timed out for server $serverName ($serverId)")
            SyncResult(
                serverId = serverId,
                serverName = serverName,
                success = false,
                error = "Timeout after ${timeoutMs}ms",
                durationMs = timeoutMs
            )
        } catch (e: CancellationException) {
            throw e // Don't catch cancellation
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Operation failed for server $serverName ($serverId)", e)
            SyncResult(
                serverId = serverId,
                serverName = serverName,
                success = false,
                error = e.message ?: "Unknown error",
                durationMs = duration
            )
        }
    }

    /**
     * Execute a boolean operation on multiple servers in parallel.
     * Returns true if ANY server succeeded.
     */
    suspend fun executeParallelBoolean(
        servers: List<Pair<Long, String>>,
        maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        operation: suspend (serverId: Long) -> Boolean
    ): ParallelSyncResult<Boolean> {
        return executeParallel(servers, maxConcurrency, timeoutMs) { serverId ->
            val result = operation(serverId)
            if (!result) throw Exception("Operation returned false")
            result
        }
    }

    /**
     * Execute with fast-fail: returns as soon as ONE server succeeds.
     * Useful for read operations where we just need data from any server.
     */
    suspend fun <T> executeFirstSuccess(
        servers: List<Pair<Long, String>>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        operation: suspend (serverId: Long) -> T
    ): T? = coroutineScope {
        val deferred = servers.map { (serverId, serverName) ->
            async {
                try {
                    withTimeout(timeoutMs) {
                        operation(serverId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Server $serverName failed: ${e.message}")
                    null
                }
            }
        }

        // Return first successful result
        var result: T? = null
        for (d in deferred) {
            val r = d.await()
            if (r != null) {
                result = r
                // Cancel remaining operations
                deferred.forEach { it.cancel() }
                break
            }
        }
        result
    }

    /**
     * Race mode: start all operations and return as soon as first succeeds.
     * Cancels remaining operations.
     */
    suspend fun <T> race(
        servers: List<Pair<Long, String>>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        operation: suspend (serverId: Long) -> T
    ): SyncResult<T>? = coroutineScope {
        if (servers.isEmpty()) return@coroutineScope null

        val startTime = System.currentTimeMillis()

        select<SyncResult<T>?> {
            servers.forEach { (serverId, serverName) ->
                async {
                    try {
                        withTimeout(timeoutMs) {
                            val result = operation(serverId)
                            val duration = System.currentTimeMillis() - startTime
                            SyncResult(
                                serverId = serverId,
                                serverName = serverName,
                                success = true,
                                data = result,
                                durationMs = duration
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }.onAwait { it }
            }
        }
    }

    /**
     * Alternative race implementation using select.
     * Note: Requires kotlinx.coroutines.selects import
     */
    private suspend inline fun <T> select(crossinline block: SelectBuilder<T>.() -> Unit): T {
        return kotlinx.coroutines.selects.select(block)
    }
}

// Extension for select builder
private typealias SelectBuilder<T> = kotlinx.coroutines.selects.SelectBuilder<T>
