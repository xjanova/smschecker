package com.thaiprompt.smschecker.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
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
    @Volatile
    private var isInitialized = false
    private val pendingQueue = CopyOnWriteArrayList<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // TextToSpeech must be created on a thread with a Looper (Main thread)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            tts = TextToSpeech(context, this)
        } else {
            mainHandler.post { tts = TextToSpeech(context, this) }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyTtsLanguage()

            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.9f)

            isInitialized = true
            Log.d(TAG, "TTS initialized successfully")

            // Speak any queued messages
            val queued = ArrayList(pendingQueue)
            pendingQueue.clear()
            queued.forEach { speakOnMainThread(it) }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    /**
     * Apply TTS language based on settings.
     * "auto" follows app language, "th" forces Thai, "en" forces English.
     */
    private fun applyTtsLanguage() {
        val ttsLang = secureStorage.getTtsLanguage()
        val langKey = if (ttsLang == "auto") secureStorage.getLanguage() else ttsLang
        val locale = if (langKey == "en") Locale.ENGLISH else Locale("th", "TH")

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "$langKey TTS not available, using default locale")
            tts?.setLanguage(Locale.getDefault())
        }
    }

    /**
     * Get the effective language key for building messages.
     */
    private fun getEffectiveLangKey(): String {
        val ttsLang = secureStorage.getTtsLanguage()
        return if (ttsLang == "auto") secureStorage.getLanguage() else ttsLang
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

        speakOnMainThread(text)
    }

    /**
     * Speak text regardless of TTS enabled state. Used for preview/testing.
     */
    fun speakPreview(text: String) {
        if (!isInitialized) {
            pendingQueue.add(text)
            return
        }
        speakOnMainThread(text)
    }

    /**
     * Ensures TTS.speak() is called on the Main thread for reliable operation.
     * Android's TextToSpeech works best when called from the thread that created it.
     */
    private fun speakOnMainThread(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyTtsLanguage()
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${System.currentTimeMillis()}")
        } else {
            mainHandler.post {
                applyTtsLanguage()
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${System.currentTimeMillis()}")
            }
        }
    }

    fun speakTransaction(bankName: String, amount: String, isCredit: Boolean, orderNumber: String? = null) {
        if (!isTtsEnabled()) return

        val speakBank = secureStorage.isTtsSpeakBank()
        val speakAmount = secureStorage.isTtsSpeakAmount()
        val speakType = secureStorage.isTtsSpeakType()
        val speakOrder = secureStorage.isTtsSpeakOrder()

        val message = buildTransactionMessage(
            bankName = bankName,
            amount = amount,
            isCredit = isCredit,
            orderNumber = orderNumber,
            speakBank = speakBank,
            speakAmount = speakAmount,
            speakType = speakType,
            speakOrder = speakOrder
        )

        if (message.isNotBlank()) {
            speak(message)
        }
    }

    /**
     * Build a transaction message for TTS based on settings.
     * Also used by the preview button.
     */
    fun buildTransactionMessage(
        bankName: String,
        amount: String,
        isCredit: Boolean,
        orderNumber: String? = null,
        speakBank: Boolean = true,
        speakAmount: Boolean = true,
        speakType: Boolean = true,
        speakOrder: Boolean = true
    ): String {
        val langKey = getEffectiveLangKey()

        return buildString {
            if (langKey == "en") {
                if (speakType) {
                    append(if (isCredit) "Income" else "Expense")
                }
                if (speakBank) {
                    if (isNotEmpty()) append(" from ")
                    append(bankName)
                }
                if (speakAmount) {
                    if (isNotEmpty()) append(", ")
                    append("amount $amount baht")
                }
                if (speakOrder && orderNumber != null) {
                    append(". Matched with order $orderNumber")
                }
                if (isNotEmpty() && !endsWith(".")) append(".")
            } else {
                // Thai
                if (speakType) {
                    append(if (isCredit) "เงินเข้า" else "เงินออก")
                }
                if (speakBank) {
                    if (isNotEmpty()) append(" ")
                    append(bankName)
                }
                if (speakAmount) {
                    if (isNotEmpty()) append(" ")
                    append("จำนวน $amount บาท")
                }
                if (speakOrder && orderNumber != null) {
                    if (isNotEmpty()) append(" ")
                    append("ตรงกับออเดอร์ $orderNumber")
                }
            }
        }
    }

    fun stop() {
        mainHandler.post { tts?.stop() }
    }

    fun shutdown() {
        mainHandler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }
}
