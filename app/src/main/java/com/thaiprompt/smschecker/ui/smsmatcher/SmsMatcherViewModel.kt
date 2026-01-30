package com.thaiprompt.smschecker.ui.smsmatcher

import android.app.Application
import android.net.Uri
import android.provider.Telephony
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.SmsSenderRuleDao
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long
)

data class SmsMatcherState(
    val rules: List<SmsSenderRule> = emptyList(),
    val recentSms: List<SmsMessage> = emptyList(),
    val uniqueSenders: List<Pair<String, String>> = emptyList(), // sender to sample message
    val showAddDialog: Boolean = false,
    val selectedSender: String = "",
    val selectedSample: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class SmsMatcherViewModel @Inject constructor(
    private val application: Application,
    private val smsSenderRuleDao: SmsSenderRuleDao
) : ViewModel() {

    private val _state = MutableStateFlow(SmsMatcherState())
    val state: StateFlow<SmsMatcherState> = _state.asStateFlow()

    init {
        loadRules()
        loadRecentSms()
    }

    private fun loadRules() {
        viewModelScope.launch {
            smsSenderRuleDao.getAllRules().collect { rules ->
                _state.update { it.copy(rules = rules) }
            }
        }
    }

    private fun loadRecentSms() {
        viewModelScope.launch {
            try {
                val messages = mutableListOf<SmsMessage>()
                val uri = Uri.parse("content://sms/inbox")
                val cursor = application.contentResolver.query(
                    uri,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )

                cursor?.use {
                    val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    var count = 0
                    while (it.moveToNext() && count < 100) {
                        val address = it.getString(addressIdx) ?: continue
                        val body = it.getString(bodyIdx) ?: continue
                        val date = it.getLong(dateIdx)
                        messages.add(SmsMessage(address, body, date))
                        count++
                    }
                }

                // Group by unique sender, take latest message as sample
                val uniqueSenders = messages
                    .groupBy { it.sender }
                    .map { (sender, msgs) -> sender to msgs.first().body }
                    .take(30)

                _state.update {
                    it.copy(
                        recentSms = messages,
                        uniqueSenders = uniqueSenders,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun showAddRuleDialog(sender: String, sampleMessage: String) {
        _state.update {
            it.copy(
                showAddDialog = true,
                selectedSender = sender,
                selectedSample = sampleMessage
            )
        }
    }

    fun hideAddRuleDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun addRule(senderAddress: String, bankCode: String, sampleMessage: String) {
        viewModelScope.launch {
            val rule = SmsSenderRule(
                senderAddress = senderAddress,
                bankCode = bankCode,
                sampleMessage = sampleMessage
            )
            smsSenderRuleDao.insert(rule)
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun toggleRule(rule: SmsSenderRule) {
        viewModelScope.launch {
            smsSenderRuleDao.update(rule.copy(isActive = !rule.isActive))
        }
    }

    fun deleteRule(rule: SmsSenderRule) {
        viewModelScope.launch {
            smsSenderRuleDao.delete(rule)
        }
    }
}
