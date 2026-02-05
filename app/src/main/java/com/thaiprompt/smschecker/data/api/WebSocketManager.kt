package com.thaiprompt.smschecker.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.security.SecureStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket Manager for real-time communication with multiple servers.
 *
 * Features:
 * - Maintains persistent WebSocket connections per server
 * - Auto-reconnects on disconnect with exponential backoff
 * - Network-aware: pauses when offline, reconnects when online
 * - Heartbeat/ping to keep connections alive
 * - Thread-safe connection management
 */
@Singleton
class WebSocketManager @Inject constructor(
    private val context: Context,
    private val serverConfigDao: ServerConfigDao,
    private val secureStorage: SecureStorage,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "WebSocketManager"
        private const val PING_INTERVAL_MS = 30_000L  // 30 seconds
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        private const val CONNECTION_TIMEOUT_MS = 15_000L
    }

    // Connection states per server
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    data class ServerConnectionState(
        val serverId: Long,
        val serverName: String,
        val state: ConnectionState,
        val lastError: String? = null,
        val reconnectAttempt: Int = 0
    )

    // State flows for UI observation
    private val _connectionStates = MutableStateFlow<Map<Long, ServerConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<Long, ServerConnectionState>> = _connectionStates

    private val _overallConnected = MutableStateFlow(false)
    val overallConnected: StateFlow<Boolean> = _overallConnected

    // Internal state
    private val webSockets = ConcurrentHashMap<Long, WebSocket>()
    private val reconnectDelays = ConcurrentHashMap<Long, Long>()
    private val reconnectJobs = ConcurrentHashMap<Long, Job>()
    private val pingJobs = ConcurrentHashMap<Long, Job>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isNetworkAvailable = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Listeners for incoming messages
    private val messageListeners = mutableListOf<(Long, WebSocketMessage) -> Unit>()

    data class WebSocketMessage(
        val type: String,
        val data: JsonObject? = null
    )

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket reads
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Initialize WebSocket manager and start monitoring network.
     */
    fun initialize() {
        registerNetworkCallback()
    }

    /**
     * Connect to all active servers.
     */
    fun connectAll() {
        scope.launch {
            val activeServers = try {
                serverConfigDao.getActiveConfigs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get active servers", e)
                return@launch
            }

            // Connect to each server in parallel
            activeServers.map { server ->
                async { connectToServer(server) }
            }.awaitAll()
        }
    }

    /**
     * Connect to a specific server.
     */
    private suspend fun connectToServer(server: ServerConfig) {
        val serverId = server.id

        // Skip if already connected or connecting
        val currentState = _connectionStates.value[serverId]?.state
        if (currentState == ConnectionState.CONNECTED || currentState == ConnectionState.CONNECTING) {
            return
        }

        updateState(serverId, server.name, ConnectionState.CONNECTING)

        val apiKey = secureStorage.getApiKey(serverId)
        val deviceId = secureStorage.getDeviceId()

        if (apiKey == null || deviceId == null) {
            updateState(serverId, server.name, ConnectionState.DISCONNECTED, "Missing credentials")
            return
        }

        try {
            val wsUrl = buildWsUrl(server.baseUrl, apiKey, deviceId)
            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-Device-ID", deviceId)
                .build()

            val listener = createWebSocketListener(serverId, server.name)
            val ws = okHttpClient.newWebSocket(request, listener)
            webSockets[serverId] = ws

            Log.d(TAG, "WebSocket connecting to server ${server.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebSocket for ${server.name}", e)
            updateState(serverId, server.name, ConnectionState.DISCONNECTED, e.message)
            scheduleReconnect(serverId, server)
        }
    }

    private fun buildWsUrl(baseUrl: String, apiKey: String, deviceId: String): String {
        val normalized = baseUrl.trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return "$normalized/ws/device?api_key=$apiKey&device_id=$deviceId"
    }

    private fun createWebSocketListener(serverId: Long, serverName: String): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected to $serverName")
                reconnectDelays[serverId] = INITIAL_RECONNECT_DELAY_MS
                reconnectJobs[serverId]?.cancel()
                updateState(serverId, serverName, ConnectionState.CONNECTED)
                startPingJob(serverId, webSocket)
                updateOverallState()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, WebSocketMessage::class.java)
                    Log.d(TAG, "Received message from $serverName: ${message.type}")
                    notifyListeners(serverId, message)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse WebSocket message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing from $serverName: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed from $serverName: $code $reason")
                handleDisconnect(serverId, serverName, "Closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure from $serverName", t)
                handleDisconnect(serverId, serverName, t.message ?: "Connection failed")
            }
        }
    }

    private fun handleDisconnect(serverId: Long, serverName: String, error: String) {
        webSockets.remove(serverId)
        pingJobs[serverId]?.cancel()
        updateState(serverId, serverName, ConnectionState.DISCONNECTED, error)
        updateOverallState()

        // Schedule reconnect if network is available
        if (isNetworkAvailable) {
            scope.launch {
                val server = serverConfigDao.getById(serverId)
                if (server != null && server.isActive) {
                    scheduleReconnect(serverId, server)
                }
            }
        }
    }

    private fun scheduleReconnect(serverId: Long, server: ServerConfig) {
        // Cancel existing reconnect job
        reconnectJobs[serverId]?.cancel()

        val delay = reconnectDelays.getOrPut(serverId) { INITIAL_RECONNECT_DELAY_MS }
        val attempt = (_connectionStates.value[serverId]?.reconnectAttempt ?: 0) + 1

        updateState(serverId, server.name, ConnectionState.RECONNECTING, reconnectAttempt = attempt)

        reconnectJobs[serverId] = scope.launch {
            Log.d(TAG, "Scheduling reconnect to ${server.name} in ${delay}ms (attempt $attempt)")
            delay(delay)

            if (isActive && isNetworkAvailable) {
                connectToServer(server)
            }
        }

        // Exponential backoff
        reconnectDelays[serverId] = (delay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    private fun startPingJob(serverId: Long, webSocket: WebSocket) {
        pingJobs[serverId]?.cancel()
        pingJobs[serverId] = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                try {
                    val pingMessage = gson.toJson(mapOf("type" to "ping", "timestamp" to System.currentTimeMillis()))
                    webSocket.send(pingMessage)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send ping", e)
                }
            }
        }
    }

    /**
     * Send a message to a specific server.
     */
    fun sendMessage(serverId: Long, type: String, data: Map<String, Any>? = null): Boolean {
        val ws = webSockets[serverId] ?: return false
        return try {
            val message = mutableMapOf<String, Any>("type" to type)
            if (data != null) {
                message["data"] = data
            }
            ws.send(gson.toJson(message))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to server $serverId", e)
            false
        }
    }

    /**
     * Send a message to all connected servers.
     */
    fun broadcastMessage(type: String, data: Map<String, Any>? = null) {
        webSockets.keys.forEach { serverId ->
            sendMessage(serverId, type, data)
        }
    }

    /**
     * Disconnect from all servers.
     */
    fun disconnectAll() {
        webSockets.forEach { (serverId, ws) ->
            try {
                ws.close(1000, "Client disconnect")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing WebSocket for server $serverId", e)
            }
        }
        webSockets.clear()
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        pingJobs.values.forEach { it.cancel() }
        pingJobs.clear()
        _connectionStates.value = emptyMap()
        _overallConnected.value = false
    }

    /**
     * Disconnect from a specific server.
     */
    fun disconnect(serverId: Long) {
        reconnectJobs[serverId]?.cancel()
        pingJobs[serverId]?.cancel()
        webSockets.remove(serverId)?.close(1000, "Client disconnect")

        val states = _connectionStates.value.toMutableMap()
        states.remove(serverId)
        _connectionStates.value = states
        updateOverallState()
    }

    /**
     * Add a listener for incoming messages.
     */
    fun addMessageListener(listener: (Long, WebSocketMessage) -> Unit) {
        messageListeners.add(listener)
    }

    /**
     * Remove a message listener.
     */
    fun removeMessageListener(listener: (Long, WebSocketMessage) -> Unit) {
        messageListeners.remove(listener)
    }

    private fun notifyListeners(serverId: Long, message: WebSocketMessage) {
        mainHandler.post {
            messageListeners.forEach { listener ->
                try {
                    listener(serverId, message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in message listener", e)
                }
            }
        }
    }

    private fun updateState(
        serverId: Long,
        serverName: String,
        state: ConnectionState,
        error: String? = null,
        reconnectAttempt: Int = 0
    ) {
        val states = _connectionStates.value.toMutableMap()
        states[serverId] = ServerConnectionState(
            serverId = serverId,
            serverName = serverName,
            state = state,
            lastError = error,
            reconnectAttempt = reconnectAttempt
        )
        _connectionStates.value = states
    }

    private fun updateOverallState() {
        val anyConnected = _connectionStates.value.values.any { it.state == ConnectionState.CONNECTED }
        _overallConnected.value = anyConnected
    }

    // =====================================================================
    // Network Monitoring
    // =====================================================================

    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                isNetworkAvailable = true
                // Reconnect all disconnected servers
                scope.launch {
                    delay(1000) // Small delay to let network stabilize
                    connectAll()
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                isNetworkAvailable = false
                // Cancel all reconnect attempts
                reconnectJobs.values.forEach { it.cancel() }
                reconnectJobs.clear()
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }

        // Check initial network state
        val activeNetwork = connectivityManager.activeNetwork
        isNetworkAvailable = activeNetwork != null
    }

    /**
     * Cleanup resources when no longer needed.
     */
    fun shutdown() {
        disconnectAll()
        scope.cancel()

        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback", e)
            }
        }
        networkCallback = null
    }

    /**
     * Check if connected to at least one server.
     */
    fun isAnyConnected(): Boolean = _overallConnected.value

    /**
     * Check if connected to a specific server.
     */
    fun isConnected(serverId: Long): Boolean {
        return _connectionStates.value[serverId]?.state == ConnectionState.CONNECTED
    }

    /**
     * Get current connection state for a server.
     */
    fun getConnectionState(serverId: Long): ServerConnectionState? {
        return _connectionStates.value[serverId]
    }
}
