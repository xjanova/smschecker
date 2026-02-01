package com.thaiprompt.smschecker.ui.settings

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.ApprovalMode
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.security.SecureStorage
import com.thaiprompt.smschecker.service.TtsManager
import com.thaiprompt.smschecker.ui.theme.LanguageMode
import com.thaiprompt.smschecker.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsState(
    val servers: List<ServerConfig> = emptyList(),
    val isMonitoring: Boolean = true,
    val deviceId: String = "",
    val showAddDialog: Boolean = false,
    val addServerError: String? = null,
    val isLoading: Boolean = true,
    val approvalMode: ApprovalMode = ApprovalMode.AUTO,
    val offlineQueueCount: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val languageMode: LanguageMode = LanguageMode.THAI,
    val ttsEnabled: Boolean = false,
    val ttsLanguage: String = "auto",      // "auto", "th", "en"
    val ttsSpeakBank: Boolean = true,
    val ttsSpeakAmount: Boolean = true,
    val ttsSpeakType: Boolean = true,
    val ttsSpeakOrder: Boolean = true,
    val isNotificationListening: Boolean = false,
    val isNotificationAccessGranted: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val orderRepository: OrderRepository,
    private val secureStorage: SecureStorage,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        try {
            // Ensure device ID exists
            var deviceId = secureStorage.getDeviceId()
            if (deviceId == null) {
                deviceId = "SMSCHK-${UUID.randomUUID().toString().take(8).uppercase()}"
                secureStorage.saveDeviceId(deviceId)
            }

            _state.update {
                it.copy(
                    isMonitoring = secureStorage.isMonitoringEnabled(),
                    deviceId = deviceId,
                    approvalMode = ApprovalMode.fromApiValue(secureStorage.getApprovalMode()),
                    themeMode = ThemeMode.fromKey(secureStorage.getThemeMode()),
                    languageMode = LanguageMode.fromKey(secureStorage.getLanguage()),
                    ttsEnabled = secureStorage.isTtsEnabled(),
                    ttsLanguage = secureStorage.getTtsLanguage(),
                    ttsSpeakBank = secureStorage.isTtsSpeakBank(),
                    ttsSpeakAmount = secureStorage.isTtsSpeakAmount(),
                    ttsSpeakType = secureStorage.isTtsSpeakType(),
                    ttsSpeakOrder = secureStorage.isTtsSpeakOrder(),
                    isNotificationListening = secureStorage.isNotificationListeningEnabled()
                )
            }
        } catch (_: Exception) {
            // SecureStorage initialization may fail on first launch
        }

        viewModelScope.launch {
            try {
                repository.getAllServerConfigs().collect { servers ->
                    _state.update { it.copy(servers = servers, isLoading = false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }

        viewModelScope.launch {
            try {
                orderRepository.getOfflineQueueCount().collect { count ->
                    _state.update { it.copy(offlineQueueCount = count) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) { }
        }
    }

    fun toggleMonitoring() {
        try {
            val newValue = !_state.value.isMonitoring
            secureStorage.setMonitoringEnabled(newValue)
            _state.update { it.copy(isMonitoring = newValue) }
        } catch (_: Exception) { }
    }

    fun showAddServerDialog() {
        _state.update { it.copy(showAddDialog = true) }
    }

    fun hideAddServerDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun addServer(name: String, url: String, apiKey: String, secretKey: String, isDefault: Boolean, deviceId: String? = null) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(addServerError = null) }

                if (deviceId != null) {
                    secureStorage.saveDeviceId(deviceId)
                    _state.update { it.copy(deviceId = deviceId) }
                }

                repository.saveServerConfig(
                    name = name,
                    baseUrl = url,
                    apiKey = apiKey,
                    secretKey = secretKey,
                    isDefault = isDefault
                )
                _state.update { it.copy(showAddDialog = false, addServerError = null) }

                try {
                    orderRepository.fetchOrders()
                    repository.syncAllUnsynced()
                } catch (_: Exception) { }
            } catch (e: IllegalStateException) {
                _state.update { it.copy(addServerError = e.message ?: "Server URL already exists") }
            } catch (e: Exception) {
                _state.update { it.copy(addServerError = "Error: ${e.message}") }
            }
        }
    }

    fun clearAddServerError() {
        _state.update { it.copy(addServerError = null) }
    }

    fun deleteServer(config: ServerConfig) {
        viewModelScope.launch {
            try { repository.deleteServerConfig(config) } catch (_: Exception) { }
        }
    }

    fun toggleServerActive(config: ServerConfig) {
        viewModelScope.launch {
            try { repository.toggleServerActive(config) } catch (_: Exception) { }
        }
    }

    fun setApprovalMode(mode: ApprovalMode) {
        try {
            secureStorage.setApprovalMode(mode.apiValue)
            _state.update { it.copy(approvalMode = mode) }
        } catch (_: Exception) { }
        viewModelScope.launch {
            try { orderRepository.updateApprovalMode(mode) } catch (_: Exception) { }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        secureStorage.setThemeMode(mode.key)
        _state.update { it.copy(themeMode = mode) }
    }

    fun setLanguageMode(mode: LanguageMode) {
        secureStorage.setLanguage(mode.key)
        _state.update { it.copy(languageMode = mode) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        secureStorage.setTtsEnabled(enabled)
        _state.update { it.copy(ttsEnabled = enabled) }
    }

    fun setTtsLanguage(lang: String) {
        secureStorage.setTtsLanguage(lang)
        _state.update { it.copy(ttsLanguage = lang) }
    }

    fun setTtsSpeakBank(enabled: Boolean) {
        secureStorage.setTtsSpeakBank(enabled)
        _state.update { it.copy(ttsSpeakBank = enabled) }
    }

    fun setTtsSpeakAmount(enabled: Boolean) {
        secureStorage.setTtsSpeakAmount(enabled)
        _state.update { it.copy(ttsSpeakAmount = enabled) }
    }

    fun setTtsSpeakType(enabled: Boolean) {
        secureStorage.setTtsSpeakType(enabled)
        _state.update { it.copy(ttsSpeakType = enabled) }
    }

    fun setTtsSpeakOrder(enabled: Boolean) {
        secureStorage.setTtsSpeakOrder(enabled)
        _state.update { it.copy(ttsSpeakOrder = enabled) }
    }

    fun previewTts() {
        val s = _state.value
        val message = ttsManager.buildTransactionMessage(
            bankName = "KBANK",
            amount = "1,500.00",
            isCredit = true,
            orderNumber = "ORD-12345",
            speakBank = s.ttsSpeakBank,
            speakAmount = s.ttsSpeakAmount,
            speakType = s.ttsSpeakType,
            speakOrder = s.ttsSpeakOrder
        )
        if (message.isNotBlank()) {
            ttsManager.speakPreview(message)
        }
    }

    fun checkNotificationAccess(context: Context) {
        try {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            val packageName = context.packageName
            val isGranted = enabledListeners.contains(packageName)
            _state.update { it.copy(isNotificationAccessGranted = isGranted) }
        } catch (_: Exception) { }
    }

    fun toggleNotificationListening() {
        try {
            val newValue = !_state.value.isNotificationListening
            secureStorage.setNotificationListeningEnabled(newValue)
            _state.update { it.copy(isNotificationListening = newValue) }
        } catch (_: Exception) { }
    }
}
