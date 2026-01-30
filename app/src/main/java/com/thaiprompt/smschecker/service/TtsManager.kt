package com.thaiprompt.smschecker.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingQueue = mutableListOf<String>()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Try Thai first, fallback to default
            val thaiResult = tts?.setLanguage(Locale("th", "TH"))
            if (thaiResult == TextToSpeech.LANG_MISSING_DATA || thaiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Thai TTS not available, using default locale")
                tts?.setLanguage(Locale.getDefault())
            }

            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.9f)

            isInitialized = true
            Log.d(TAG, "TTS initialized successfully")

            // Speak any queued messages
            pendingQueue.forEach { speak(it) }
            pendingQueue.clear()
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    fun isTtsEnabled(): Boolean = secureStorage.isTtsEnabled()

    fun setTtsEnabled(enabled: Boolean) {
        secureStorage.setTtsEnabled(enabled)
    }

    fun speak(text: String) {
        if (!isTtsEnabled()) return

        if (!isInitialized) {
            pendingQueue.add(text)
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${System.currentTimeMillis()}")
    }

    fun speakTransaction(bankName: String, amount: String, isCredit: Boolean, orderNumber: String? = null) {
        if (!isTtsEnabled()) return

        val langKey = secureStorage.getLanguage()
        val message = buildString {
            if (langKey == "en") {
                if (isCredit) {
                    append("Income from $bankName, amount $amount baht.")
                } else {
                    append("Expense from $bankName, amount $amount baht.")
                }
                if (orderNumber != null) {
                    append(" Matched with order $orderNumber.")
                }
            } else {
                // Thai
                if (isCredit) {
                    append("เงินเข้า $bankName จำนวน $amount บาท")
                } else {
                    append("เงินออก $bankName จำนวน $amount บาท")
                }
                if (orderNumber != null) {
                    append(" ตรงกับออเดอร์ $orderNumber")
                }
            }
        }

        speak(message)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
