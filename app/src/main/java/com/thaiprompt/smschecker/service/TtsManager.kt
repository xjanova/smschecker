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
import java.util.concurrent.atomic.AtomicLong
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
    private val utteranceCounter = AtomicLong(0) // unique utterance id (กัน id ชนกันใน ms เดียว)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var cachedLangKey: String? = null // Cache language to avoid re-applying every speak

    // Audio focus: temporarily duck other audio while TTS is speaking so the user
    // can still hear the announcement while music / videos are playing.
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // 🐞 (2026-06-05) TTS plays through the MEDIA stream (USAGE_MEDIA → STREAM_MUSIC) so the
    // announcement is audible even when the phone is on silent / vibrate — media volume is
    // independent of the ringer mode.
    //
    // PREVIOUS BUG: USAGE_ASSISTANCE_SONIFICATION routes to STREAM_SYSTEM, which Android MUTES
    // in silent/vibrate mode (and when "system sounds" are turned off). A merchant keeping the
    // phone on silent therefore heard NOTHING — not even the Settings preview button. The whole
    // job of this app is to announce incoming money out loud, so it MUST use the media stream.
    // Shared between playback (setAudioAttributes) and the duck focus request so they never diverge.
    private val ttsAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
    // Note: no @Volatile needed — all reads/writes happen inside `synchronized(audioFocusLock)`.
    // Adding @Volatile would be dead weight and could confuse readers about the model.
    private var audioFocusRequest: AudioFocusRequest? = null
    private var activeUtteranceCount: Int = 0
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

            // Play through the media stream so the announcement is heard even on silent/vibrate.
            try {
                tts?.setAudioAttributes(ttsAudioAttributes)
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

            // Speak any queued messages (เรากำลังอยู่บน main thread ใน onInit — เรียก speakNowOnMain ได้ตรงๆ)
            val queued = ArrayList(pendingQueue)
            pendingQueue.clear()
            queued.forEach { speakNowOnMain(it) }
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
                        audioFocusRequest = AudioFocusRequest.Builder(
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                        )
                            .setAudioAttributes(ttsAudioAttributes)
                            .setOnAudioFocusChangeListener(audioFocusListener)
                            .build()
                    }
                    audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(
                        audioFocusListener,
                        AudioManager.STREAM_MUSIC,
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
            abandonFocusNow()
        }
    }

    /**
     * 🐞 (2026-06-04) บังคับคืน audio focus + reset refcount — ใช้ตอน stop()/shutdown() ที่ flush
     * utterance ทำให้ onDone ไม่ถูกเรียกครบคู่กับ onStart → activeUtteranceCount ค้าง > 0 →
     * focus ไม่เคยถูกคืน → เพลง/วิดีโอของแอปอื่นโดน duck ค้างถาวรจนกว่า process ตาย
     */
    private fun forceReleaseAudioFocus() {
        synchronized(audioFocusLock) {
            activeUtteranceCount = 0
            abandonFocusNow()
        }
    }

    // ต้องเรียกภายใน synchronized(audioFocusLock) เท่านั้น
    private fun abandonFocusNow() {
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
        enqueueOrSpeak(text)
    }

    /**
     * Speak text regardless of TTS enabled state. Used for preview/testing.
     */
    fun speakPreview(text: String) {
        enqueueOrSpeak(text)
    }

    /**
     * 🐞 (2026-06-04) ตัดสินใจ "queue หรือพูดเลย" บน main thread เสมอ — serialized กับ onInit
     *   (ซึ่งรันบน main thread เช่นกัน) เพื่อปิด race เดิม: speak อ่าน isInitialized=false → ถูก
     *   deschedule → onInit ตั้ง true + drain queue (ว่าง) → speak ค่อย add → ไม่มีใคร drain อีก
     *   → "ประกาศแรกหลัง init หาย". ตอนนี้ทั้งเช็ค-แล้ว-enqueue และ drain อยู่บน main thread เดียวกัน
     */
    private fun enqueueOrSpeak(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            enqueueOrSpeakOnMain(text)
        } else {
            mainHandler.post { enqueueOrSpeakOnMain(text) }
        }
    }

    /** ต้องเรียกบน main thread เท่านั้น */
    private fun enqueueOrSpeakOnMain(text: String) {
        if (!isInitialized) {
            pendingQueue.add(text)
            return
        }
        speakNowOnMain(text)
    }

    /**
     * พูดจริง — ต้องเรียกบน main thread เท่านั้น (TextToSpeech ทำงานดีสุดจาก thread ที่สร้างมัน)
     */
    private fun speakNowOnMain(text: String) {
        applyTtsLanguage()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${utteranceCounter.incrementAndGet()}")
    }

    fun speakTransaction(
        bankName: String,
        amount: String,
        isCredit: Boolean,
        orderNumber: String? = null,
        productName: String? = null,
        customerName: String? = null
    ) {
        if (!isTtsEnabled()) return

        val speakBank = secureStorage.isTtsSpeakBank()
        val speakAmount = secureStorage.isTtsSpeakAmount()
        val speakType = secureStorage.isTtsSpeakType()
        val speakOrder = secureStorage.isTtsSpeakOrder()
        val speakProduct = secureStorage.isTtsSpeakProduct()
        val speakCustomer = secureStorage.isTtsSpeakCustomer()

        val message = buildTransactionMessage(
            bankName = bankName,
            amount = amount,
            isCredit = isCredit,
            orderNumber = orderNumber,
            productName = productName,
            customerName = customerName,
            speakBank = speakBank,
            speakAmount = speakAmount,
            speakType = speakType,
            speakOrder = speakOrder,
            speakProduct = speakProduct,
            speakCustomer = speakCustomer
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
        customerName: String? = null,
        speakBank: Boolean = true,
        speakAmount: Boolean = true,
        speakType: Boolean = true,
        speakOrder: Boolean = true,
        speakProduct: Boolean = true,
        speakCustomer: Boolean = true
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
                var inDetail = false
                if (speakOrder && orderNumber != null) {
                    append(". Matched with order $orderNumber")
                    inDetail = true
                }
                if (speakCustomer && customerName != null) {
                    append(if (inDetail) ", owner: $customerName" else ". Owner: $customerName")
                    inDetail = true
                }
                if (speakProduct && productName != null) {
                    append(if (inDetail) ", product: $productName" else ". Product: $productName")
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
                if (speakCustomer && customerName != null) {
                    if (isNotEmpty()) append(" ")
                    append("เจ้าของบิล $customerName")
                }
                if (speakProduct && productName != null) {
                    if (isNotEmpty()) append(" ")
                    append("สินค้า $productName")
                }
            }
        }
    }

    fun stop() {
        mainHandler.post {
            tts?.stop()
            // 🐞 (2026-06-04) tts.stop() flush utterance ที่ค้าง → onDone อาจไม่ถูกเรียก → คืน focus ทันที
            forceReleaseAudioFocus()
        }
    }

    fun shutdown() {
        mainHandler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            forceReleaseAudioFocus()
        }
    }
}
