package com.thaiprompt.smschecker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.security.SecureStorage
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
    val themeMode: String = "system"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
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
                themeMode = secureStorage.getThemeMode()
            )
        }

        viewModelScope.launch {
            repository.getAllServerConfigs().collect { servers ->
                _state.update { it.copy(servers = servers, isLoading = false) }
            }
        }
    }

    fun toggleMonitoring() {
        val newValue = !_state.value.isMonitoring
        secureStorage.setMonitoringEnabled(newValue)
        _state.update { it.copy(isMonitoring = newValue) }
    }

    fun showAddServerDialog() {
        _state.update { it.copy(showAddDialog = true) }
    }

    fun hideAddServerDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun addServer(name: String, url: String, apiKey: String, secretKey: String, isDefault: Boolean) {
        viewModelScope.launch {
            repository.saveServerConfig(
                name = name,
                baseUrl = url,
                apiKey = apiKey,
                secretKey = secretKey,
                isDefault = isDefault
            )
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun deleteServer(config: ServerConfig) {
        viewModelScope.launch {
            repository.deleteServerConfig(config)
        }
    }

    fun toggleServerActive(config: ServerConfig) {
        viewModelScope.launch {
            repository.toggleServerActive(config)
        }
    }

    fun setThemeMode(mode: String) {
        secureStorage.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }
}
