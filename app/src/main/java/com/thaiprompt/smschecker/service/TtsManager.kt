package com.thaiprompt.smschecker.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    @Volatile
    private var cachedLangKey: String? = null // Cache language to avoid re-applying every speak

    // Audio focus: temporarily duck other audio while TTS is speaking so the user
    // can still hear the announcement while music / videos are playing.
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    @Volatile private var audioFocusRequest: AudioFocusRequest? = null
    @Volatile private var activeUtteranceCount: Int = 0
    private val audioFocusLock = Any()

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { /* no-op; transient */ }

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

            // Route TTS through the ASSISTANCE_SONIFICATION stream so it bypasses music/media
            // volume and plays even when the user has their ringer muted (this is a notification).
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set TTS audio attributes", e)
            }

            // Utterance listener — manage audio focus lifecycle.
            // Request focus on START, release on DONE/ERROR so music/video resumes.
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    requestAudioFocus()
                }
                override fun onDone(utteranceId: String?) {
                    releaseAudioFocusIfIdle()
                }
                @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
                override fun onError(utteranceId: String?) {
                    releaseAudioFocusIfIdle()
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    releaseAudioFocusIfIdle()
                }
            })

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
     * Transient audio focus (DUCK) so music plays softly while TTS speaks.
     * Reference-counted: multiple queued utterances share one focus request.
     */
    private fun requestAudioFocus() {
        synchronized(audioFocusLock) {
            activeUtteranceCount++
            if (activeUtteranceCount > 1) return // already focused
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (audioFocusRequest == null) {
                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        audioFocusRequest = AudioFocusRequest.Builder(
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                        )
                            .setAudioAttributes(attrs)
                            .setOnAudioFocusChangeListener(audioFocusListener)
                            .build()
                    }
                    audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(
                        audioFocusListener,
                        AudioManager.STREAM_NOTIFICATION,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request audio focus", e)
            }
        }
    }

    private fun releaseAudioFocusIfIdle() {
        synchronized(audioFocusLock) {
            activeUtteranceCount = (activeUtteranceCount - 1).coerceAtLeast(0)
            if (activeUtteranceCount > 0) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(audioFocusListener)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to abandon audio focus", e)
            }
        }
    }

    /**
     * Apply TTS language based on settings.
     * "auto" follows app language, "th" forces Thai, "en" forces English.
     * Uses caching to avoid unnecessary setLanguage() calls which are expensive.
     */
    private fun applyTtsLanguage() {
        val ttsLang = secureStorage.getTtsLanguage()
        val langKey = if (ttsLang == "auto") secureStorage.getLanguage() else ttsLang

        // Skip if language hasn't changed since last apply
        if (langKey == cachedLangKey) return
        cachedLangKey = langKey

        val locale = if (langKey == "en") Locale.ENGLISH else Locale("th", "TH")

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "$langKey TTS not available, using default locale")
            tts?.setLanguage(Locale.getDefault())
        }
    }

    /**
     * Call this when language settings change to force re-apply on next speak.
     */
    fun invalidateLanguageCache() {
        cachedLangKey = null
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

    fun speakTransaction(
        bankName: String,
        amount: String,
        isCredit: Boolean,
        orderNumber: String? = null,
        productName: String? = null
    ) {
        if (!isTtsEnabled()) return

        val speakBank = secureStorage.isTtsSpeakBank()
        val speakAmount = secureStorage.isTtsSpeakAmount()
        val speakType = secureStorage.isTtsSpeakType()
        val speakOrder = secureStorage.isTtsSpeakOrder()
        val speakProduct = secureStorage.isTtsSpeakProduct()

        val message = buildTransactionMessage(
            bankName = bankName,
            amount = amount,
            isCredit = isCredit,
            orderNumber = orderNumber,
            productName = productName,
            speakBank = speakBank,
            speakAmount = speakAmount,
            speakType = speakType,
            speakOrder = speakOrder,
            speakProduct = speakProduct
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
        productName: String? = null,
        speakBank: Boolean = true,
        speakAmount: Boolean = true,
        speakType: Boolean = true,
        speakOrder: Boolean = true,
        speakProduct: Boolean = true
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
                if (speakProduct && productName != null) {
                    if (speakOrder && orderNumber != null) {
                        append(", product: $productName")
                    } else {
                        append(". Product: $productName")
                    }
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
                if (speakProduct && productName != null) {
                    if (isNotEmpty()) append(" ")
                    append("สินค้า $productName")
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
