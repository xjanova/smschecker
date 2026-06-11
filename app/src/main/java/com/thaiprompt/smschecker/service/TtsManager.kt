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

/**
 * สถานะความพร้อมของเสียงพูดบนเครื่อง — ใช้แสดง warning + ปุ่มแก้ไขใน Settings
 *  READY              = พูดภาษาที่ตั้งไว้ได้
 *  ENGINE_NOT_READY   = TTS ยัง init ไม่เสร็จ (ชั่วคราว)
 *  GOOGLE_TTS_MISSING = engine ปัจจุบันไม่มีเสียงภาษาที่ต้องการ และไม่มี Google TTS ในเครื่อง
 *                       (เคส Samsung TTS ไม่มีเสียงไทย) → พาไปติดตั้งจาก Play Store (ฟรี)
 *  THAI_VOICE_MISSING = มี engine แล้วแต่ยังไม่ได้ดาวน์โหลดชุดข้อมูลเสียง → พาไปหน้าดาวน์โหลด
 */
enum class TtsVoiceStatus { READY, ENGINE_NOT_READY, GOOGLE_TTS_MISSING, THAI_VOICE_MISSING }

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"

        /**
         * 🐞 (2026-06-11) Google TTS engine — บังคับใช้ถ้าติดตั้งอยู่
         * เคสจริง: Samsung ไม่พูดแต่ Vivo พูด — Samsung ตั้ง default engine เป็น
         * Samsung TTS (com.samsung.SMT) ซึ่งส่วนใหญ่ "ไม่มีเสียงภาษาไทย" →
         * setLanguage คืน LANG_NOT_SUPPORTED → speak เงียบสนิททั้งที่โค้ดปกติ
         * Vivo/เครื่องอื่นใช้ Google TTS เป็น default อยู่แล้วเลยพูดได้
         */
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    }

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    /** engine ที่ใช้สร้าง TTS รอบปัจจุบัน (null = system default) — ไว้ตัดสินใจ fallback ตอน init fail */
    @Volatile
    private var currentEngine: String? = null
    /** กัน retry init วนลูป — fallback ได้ครั้งเดียว */
    @Volatile
    private var triedFallbackEngine = false
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
            createTts()
        } else {
            mainHandler.post { createTts() }
        }
    }

    /**
     * สร้าง TextToSpeech โดยเลือก engine ที่มีเสียงไทยแน่นอนที่สุดก่อน:
     * Google TTS (ถ้าติดตั้ง) → system default. ต้องเรียกบน main thread
     */
    private fun createTts(preferredEngine: String? = pickPreferredEngine()) {
        currentEngine = preferredEngine
        cachedLangKey = null // engine ใหม่ต้อง setLanguage ใหม่เสมอ
        tts = if (preferredEngine != null) {
            Log.i(TAG, "Creating TTS with engine: $preferredEngine")
            TextToSpeech(context, this, preferredEngine)
        } else {
            Log.i(TAG, "Creating TTS with system default engine")
            TextToSpeech(context, this)
        }
    }

    private fun pickPreferredEngine(): String? = try {
        context.packageManager.getPackageInfo(GOOGLE_TTS_PACKAGE, 0)
        GOOGLE_TTS_PACKAGE
    } catch (e: Exception) {
        null // Google TTS ไม่ได้ติดตั้ง → ใช้ default ของเครื่อง
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.i(TAG, "TTS init OK (engine=${currentEngine ?: "default"}, defaultEngine=${tts?.defaultEngine})")
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
            Log.e(TAG, "TTS initialization failed with status: $status (engine=${currentEngine ?: "default"})")
            // engine ที่ขอไป init ไม่ได้ (เช่น Google TTS ถูก disable) → ลอง engine อีกตัวหนึ่งครั้งเดียว
            if (!triedFallbackEngine) {
                triedFallbackEngine = true
                val fallback = if (currentEngine != null) null else pickPreferredEngine()
                Log.w(TAG, "Retrying TTS init with ${fallback ?: "system default"} engine")
                mainHandler.post { createTts(fallback) }
            }
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
            // เคส Samsung TTS ไม่มีเสียงไทย — log ชัดๆ ให้เห็นใน bug report + อย่า cache
            // ภาษาที่ตั้งไม่สำเร็จ (เผื่อผู้ใช้ไปติดตั้งเสียงไทยแล้วกลับมา จะได้ลองใหม่)
            Log.e(
                TAG,
                "TTS language $locale NOT available (result=$result, engine=${tts?.defaultEngine}) — " +
                    "falling back to device default locale"
            )
            cachedLangKey = null
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
        ensureMediaVolumeAudible()
        applyTtsLanguage()
        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${utteranceCounter.incrementAndGet()}")
        if (result != TextToSpeech.SUCCESS) {
            // ERROR ที่นี่ = engine ตาย/ไม่พร้อม (ไม่ใช่เรื่อง volume) — ให้เห็นใน log เสมอ
            Log.e(TAG, "tts.speak() returned $result (engine=${tts?.defaultEngine})")
        }
    }

    /**
     * 🐞 (2026-06-10) TTS เล่นผ่าน media stream (ตาม fix 2026-06-05) แต่เครื่องร้านค้าจำนวนมาก
     * ลด/ปิด "เสียงสื่อ" ไว้ (คนละปุ่มกับเสียงเรียกเข้า) → TTS เงียบสนิทรวมถึงปุ่มทดลองฟัง
     * หน้าที่หลักของแอพคือประกาศเงินเข้าให้ได้ยิน — ก่อนพูดทุกครั้ง ถ้า media volume ต่ำกว่า
     * 60% ของ max ให้ดันขึ้นเป็น 60% (ไม่ลดเสียงผู้ใช้ที่ตั้งสูงกว่านั้น)
     */
    private fun ensureMediaVolumeAudible() {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val floor = (max * 0.6f).toInt().coerceAtLeast(1)
            if (current < floor) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, floor, 0)
                Log.i(TAG, "Media volume raised $current -> $floor (max $max) so TTS is audible")
            }
        } catch (e: Exception) {
            // SecurityException ได้ในโหมด Do-Not-Disturb แบบเข้มงวด — พูดต่อด้วย volume เดิม
            Log.w(TAG, "ensureMediaVolumeAudible failed", e)
        }
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

    // ══════════════════════════════════════════════════════════════
    // Voice health check + ตัวช่วยติดตั้ง (2026-06-11)
    // เคส Samsung: ไม่มี Google TTS / มีแต่ยังไม่โหลดชุดเสียงไทย → แอปตรวจเองแล้ว
    // พาผู้ใช้ไปติดตั้ง (Google TTS ฟรี สังเคราะห์บนเครื่อง ไม่มีค่า API)
    // ══════════════════════════════════════════════════════════════

    fun isGoogleTtsInstalled(): Boolean = pickPreferredEngine() != null

    /** locale ที่จะใช้พูดจริงตามการตั้งค่า (ตรงกับ applyTtsLanguage) */
    private fun effectiveSpeakLocale(): Locale =
        if (getEffectiveLangKey() == "en") Locale.ENGLISH else Locale("th", "TH")

    /**
     * ตรวจว่าเครื่องนี้พูดภาษาที่ตั้งไว้ได้จริงไหม — เรียกจาก Settings เพื่อโชว์ warning
     */
    fun checkVoiceStatus(): TtsVoiceStatus {
        val engine = tts
        if (engine == null || !isInitialized) {
            return if (isGoogleTtsInstalled()) TtsVoiceStatus.ENGINE_NOT_READY
            else TtsVoiceStatus.GOOGLE_TTS_MISSING
        }
        val availability = try {
            engine.isLanguageAvailable(effectiveSpeakLocale())
        } catch (e: Exception) {
            Log.w(TAG, "isLanguageAvailable failed", e)
            return TtsVoiceStatus.ENGINE_NOT_READY
        }
        return when {
            availability >= TextToSpeech.LANG_AVAILABLE -> TtsVoiceStatus.READY
            availability == TextToSpeech.LANG_MISSING_DATA -> TtsVoiceStatus.THAI_VOICE_MISSING
            // LANG_NOT_SUPPORTED: engine นี้ไม่มีภาษานี้เลย — ถ้ามี Google TTS อยู่แล้ว
            // แปลว่าเราใช้มันอยู่แต่ข้อมูลเสียงยังไม่มา ให้พาไปหน้าดาวน์โหลดเสียง
            isGoogleTtsInstalled() -> TtsVoiceStatus.THAI_VOICE_MISSING
            else -> TtsVoiceStatus.GOOGLE_TTS_MISSING
        }
    }

    /**
     * ถ้าตอนเปิดแอพยังไม่มี Google TTS แล้วผู้ใช้เพิ่งติดตั้ง — สร้าง TTS ใหม่ให้ใช้ Google
     * เรียกตอนกลับเข้าหน้า Settings (ON_RESUME)
     */
    fun reinitWithGoogleIfNewlyInstalled() {
        if (currentEngine == null && isGoogleTtsInstalled()) {
            Log.i(TAG, "Google TTS detected after startup — reinitializing with it")
            mainHandler.post {
                try { tts?.shutdown() } catch (_: Exception) { }
                isInitialized = false
                triedFallbackEngine = false
                createTts()
            }
        }
    }

    /** เปิด Play Store หน้า Google Speech Services (ฟรี) — เคสเครื่องไม่มี Google TTS */
    fun openGoogleTtsInstallPage() {
        val market = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("market://details?id=$GOOGLE_TTS_PACKAGE")
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_TTS_PACKAGE")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot open Play Store for Google TTS", e2)
            }
        }
    }

    /** เปิดหน้าดาวน์โหลดชุดข้อมูลเสียงของ engine (เคสติดตั้ง Google TTS แล้วแต่ยังไม่มีเสียงไทย) */
    fun openVoiceDataInstaller() {
        val installIntent = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        currentEngine?.let { installIntent.setPackage(it) }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            // บางเครื่องไม่มี activity รับ intent นี้ → เปิดหน้าตั้งค่า TTS ของระบบแทน
            try {
                context.startActivity(
                    android.content.Intent("com.android.settings.TTS_SETTINGS")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot open TTS data installer or settings", e2)
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
