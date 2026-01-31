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
            val queued = ArrayList(pendingQueue)
            pendingQueue.clear()
            queued.forEach { speakOnMainThread(it) }
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

        speakOnMainThread(text)
    }

    /**
     * Ensures TTS.speak() is called on the Main thread for reliable operation.
     * Android's TextToSpeech works best when called from the thread that created it.
     */
    private fun speakOnMainThread(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${System.currentTimeMillis()}")
        } else {
            mainHandler.post {
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${System.currentTimeMillis()}")
            }
        }
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
