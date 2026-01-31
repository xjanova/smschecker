package com.thaiprompt.smschecker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.ApprovalMode
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.security.SecureStorage
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
    val isLoading: Boolean = true,
    val approvalMode: ApprovalMode = ApprovalMode.AUTO,
    val offlineQueueCount: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val languageMode: LanguageMode = LanguageMode.THAI,
    val ttsEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val orderRepository: OrderRepository,
    private val secureStorage: SecureStorage
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
                    ttsEnabled = secureStorage.isTtsEnabled()
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
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }

        viewModelScope.launch {
            try {
                orderRepository.getOfflineQueueCount().collect { count ->
                    _state.update { it.copy(offlineQueueCount = count) }
                }
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

    fun addServer(name: String, url: String, apiKey: String, secretKey: String, isDefault: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveServerConfig(
                    name = name,
                    baseUrl = url,
                    apiKey = apiKey,
                    secretKey = secretKey,
                    isDefault = isDefault
                )
                _state.update { it.copy(showAddDialog = false) }
            } catch (_: Exception) { }
        }
    }

    fun deleteServer(config: ServerConfig) {
        viewModelScope.launch {
            try {
                repository.deleteServerConfig(config)
            } catch (_: Exception) { }
        }
    }

    fun toggleServerActive(config: ServerConfig) {
        viewModelScope.launch {
            try {
                repository.toggleServerActive(config)
            } catch (_: Exception) { }
        }
    }

    fun setApprovalMode(mode: ApprovalMode) {
        try {
            secureStorage.setApprovalMode(mode.apiValue)
            _state.update { it.copy(approvalMode = mode) }
        } catch (_: Exception) { }
        viewModelScope.launch {
            try {
                orderRepository.updateApprovalMode(mode)
            } catch (_: Exception) {
                // Server update failed, local setting still persisted
            }
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
}
