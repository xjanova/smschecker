package com.thaiprompt.smschecker.ui.splash

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.thaiprompt.smschecker.R
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/** สีพื้นคลิป — ต้องตรงกับเฟรมแรกของ intro.mp4 ไม่งั้นจะเห็นกระพริบตอนสลับ */
private val SplashNavy = Color(0xFF0B1426)

/**
 * กันค้าง: ต่อให้เครื่องถอดรหัสวิดีโอไม่ได้เลย ก็ต้องเข้าแอพภายในเวลานี้
 *
 * ⚠️ ต้องเริ่มนับ "หลังวาดเฟรมแรกจริง" ไม่ใช่ตอน compose
 *    เครื่องช้า/รอบ cold start ระบบค้างที่ splash ของ Android ได้หลายวินาที
 *    (วัดบน emulator: `Displayed ... +4s926ms`) ถ้านับตั้งแต่ compose
 *    ตัวจับเวลาจะหมดตั้งแต่จอยังไม่ทันโผล่ = คลิปไม่ได้ออกอากาศเลยสักครั้ง
 */
private const val NO_VIDEO_MS = 6_000L

/**
 * เพดานสูงสุด — ถึงจะโหลดไม่เสร็จก็ต้องปล่อยเข้าแอพ
 *
 * แอพนี้อ่านบิลจากฐานข้อมูลในเครื่องได้อยู่แล้ว ถ้าเน็ตร้านล่มแล้วเราขังไว้ที่หน้าโหลด
 * = ร้านดูบิลไม่ได้ทั้งที่ข้อมูลเก่ายังอยู่ครบ ซึ่งแย่กว่าการเข้าไปเจอข้อมูลเก่าเยอะ
 */
private const val HARD_CAP_MS = 22_000L

/**
 * หน้าเปิดแอพ — คลิปโลโก้ 10 วินาที ที่ทำหน้าที่เป็น "หน้าโหลด" ไปด้วย
 *
 * เจ้าของสั่ง: "10 วินาทีไม่นาน ทำเป็น loading อยู่แล้วในระหว่างเล่นวีดีโอ"
 * → ระหว่างคลิปเล่น แอพซิงค์ข้อมูลจากเซิร์ฟเวอร์ + ตรวจสิทธิ์ใช้งานไปพร้อมกัน
 *   พอคลิปจบ ข้อมูลก็สดแล้ว ไม่ต้องมานั่งดูสปินเนอร์ในแอพอีกรอบ
 *
 * จังหวะจบ = **คลิปจบครบรอบ และ โหลดเสร็จ** (เอาอันที่ช้ากว่า)
 *   - โหลดเสร็จก่อนคลิปจบ → ดูคลิปให้จบสวย ๆ (10 วิ เจ้าของบอกว่าไม่นาน)
 *   - คลิปจบก่อนโหลดเสร็จ → **วนคลิปซ้ำ** แทนที่จะค้างเฟรมสุดท้ายนิ่ง ๆ
 *
 * ทำไมใช้ TextureView ไม่ใช่ VideoView:
 *   VideoView วางบน SurfaceView ซึ่ง "เจาะรู" ทะลุ view ที่ซ้อนอยู่ → เอา Compose
 *   ไปวางทับแล้วลำดับชั้นเพี้ยน และจังหวะสร้าง surface มักแวบดำ
 *   TextureView เป็น view ปกติ ซ้อน/หรี่ความทึบ/ครอปด้วย matrix ได้ตามใจ
 *
 * ทางออกมีหลายทาง (คลิปจบ+พร้อม / แตะข้าม / เล่นไม่ได้ / หมดเวลา) และเกิดพร้อมกันได้
 * เช่นแตะรัวตอนคลิปกำลังจบ → ต้องกันเรียก onFinished ซ้ำ ไม่งั้นสลับหน้าซ้อน
 *
 * @param isReady    งานตอนเปิดแอพเสร็จแล้วหรือยัง (ซิงค์ข้อมูล + ตรวจสิทธิ์)
 * @param statusText ข้อความบอกว่ากำลังทำอะไรอยู่ — แสดงใต้โลโก้
 */
@Composable
fun IntroSplashScreen(
    isReady: Boolean,
    statusText: String,
    onFinished: () -> Unit
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val fired = remember { AtomicBoolean(false) }
    val finish: () -> Unit = { if (fired.compareAndSet(false, true)) latestOnFinished() }

    var videoDrawing by remember { mutableStateOf(false) }
    var playedThrough by remember { mutableStateOf(false) }

    // อ่านค่าล่าสุดจากใน callback ของ MediaPlayer (ซึ่งไม่ recompose ตามเรา)
    val readyNow by rememberUpdatedState(isReady)

    // เงื่อนไขจบปกติ: คลิปจบครบรอบ + โหลดเสร็จ
    LaunchedEffect(playedThrough, isReady) {
        if (playedThrough && isReady) finish()
    }

    // ⚠️ ต้องเริ่มนับหลัง withFrameNanos (= จอเราถูกวาดจริง) ไม่ใช่ตอน compose
    //    เพราะ cold start ระบบค้างที่ splash ของ Android ได้หลายวินาที
    //    (วัดบน emulator: `Displayed ... +4s926ms`) นับเร็วไปคลิปไม่ได้ออกอากาศเลย
    LaunchedEffect(videoDrawing) {
        withFrameNanos { }
        if (!videoDrawing) {
            // ยังไม่มีเฟรมวิดีโอเลย — ให้โอกาสถึงเวลานี้ ถ้ายังไม่มาถือว่าเครื่องเล่นไม่ได้
            delay(NO_VIDEO_MS)
            if (!videoDrawing) playedThrough = true
        } else {
            delay(HARD_CAP_MS)
            finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashNavy)
            // แตะที่ไหนก็ข้ามได้ — แอพนี้เปิดวันละหลายสิบครั้ง ห้ามบังคับให้ดูจนจบ
            .pointerInput(Unit) { detectTapGestures { finish() } }
    ) {
        // ภาพนิ่งสำรอง: ขึ้นทันทีตั้งแต่เฟรมแรก แล้วจางออกเมื่อวิดีโอเริ่มวาดจริง
        // ถ้าเครื่องเล่นคลิปไม่ได้ ภาพนี้อยู่ยาวจนกดข้าม/หมดเวลา — ยังดูตั้งใจ ไม่ใช่จอดำ
        StaticIntro(hidden = videoDrawing)

        IntroVideo(
            onFirstFrame = { videoDrawing = true },
            onPlayedThrough = { playedThrough = true },
            shouldReplay = { !readyNow },
            onFailed = { playedThrough = true }
        )

        LoadingStrip(
            statusText = statusText,
            isReady = isReady,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * แถบสถานะการโหลดใต้จอ — บอกว่าตอนนี้กำลังทำอะไร ไม่ใช่แค่หมุนเล่น
 *
 * ผู้ใช้ต้องแยกออกว่า "แอพกำลังทำงาน" กับ "แอพค้าง" — สปินเนอร์เปล่า ๆ แยกไม่ได้
 * พอโหลดเสร็จเปลี่ยนเป็นข้อความพร้อมใช้งาน ให้รู้ว่าที่เหลือคือรอคลิปจบเฉย ๆ
 */
@Composable
private fun LoadingStrip(
    statusText: String,
    isReady: Boolean,
    modifier: Modifier = Modifier
) {
    // ใช้ตัวของ Material เลย — indeterminate ตอนโหลด / เต็มแท่งสีเขียวตอนพร้อม
    // (เขียนแถบวิ่งเองต้องคำนวณ offset จากความกว้าง parent ซึ่งพังง่ายบนจอกว้างต่างกัน)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isReady) {
            // Material3 รุ่นในโปรเจกต์นี้ยังเป็น API เก่า — progress เป็น Float ตรง ๆ
            // ไม่ใช่แลมบ์ดา และยังไม่มี gapSize/drawStopIndicator
            LinearProgressIndicator(
                progress = 1f,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50)),
                color = Color(0xFF4ADE80),
                trackColor = Color.White.copy(alpha = 0.14f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50)),
                color = Color(0xFF7FD4E8),
                trackColor = Color.White.copy(alpha = 0.14f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = statusText,
            color = Color.White.copy(alpha = if (isReady) 0.66f else 0.80f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * ตัวเล่นคลิป — MediaPlayer + TextureView พร้อมครอปแบบ center-crop
 *
 * จอมือถือมีหลายอัตราส่วน (18:9 / 19.5:9 / 20:9) แต่คลิปเป็น 9:16 ตายตัว
 * ถ้าปล่อยให้ TextureView ยืดเต็ม view ภาพจะบิด → ต้องคำนวณ matrix ครอบเอง
 */
@Composable
private fun IntroVideo(
    onFirstFrame: () -> Unit,
    onPlayedThrough: () -> Unit,
    shouldReplay: () -> Boolean,
    onFailed: () -> Unit
) {
    val holder = remember { arrayOfNulls<MediaPlayer>(1) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { holder[0]?.release() }
            holder[0] = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                // ⚠️ TextureView ทึบโดยปริยาย = ทาสีดำทับทุกอย่างตั้งแต่ถูกวางลงจอ
                //    ทั้งที่ยังไม่มีเฟรมวิดีโอเลย → เห็นจอดำแวบก่อนคลิปเริ่ม (จับได้จากภาพจริงบน emulator)
                //    ต้องโปร่งใส + ซ่อนไว้ก่อน แล้วค่อยเผยเมื่อมีเฟรมแรกจริง
                isOpaque = false
                alpha = 0f
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val view = this@apply
                        try {
                            val mp = MediaPlayer()
                            ctx.resources.openRawResourceFd(R.raw.intro).use { afd ->
                                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            }
                            mp.setSurface(Surface(st))
                            mp.setVolume(0f, 0f) // คลิปไม่มีแทร็กเสียงอยู่แล้ว กันไว้อีกชั้น
                            mp.setOnPreparedListener { p ->
                                applyCenterCrop(view, p.videoWidth, p.videoHeight, view.width, view.height)
                                runCatching { p.start() }
                            }
                            mp.setOnInfoListener { _, what, _ ->
                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                    // มีเฟรมจริงแล้วค่อยเผยตัว view — ค่อย ๆ จางเข้าให้ชนกับภาพสำรองพอดี
                                    view.animate().alpha(1f).setDuration(200).start()
                                    onFirstFrame()
                                }
                                false
                            }
                            // จบรอบหนึ่ง — ถ้ายังโหลดไม่เสร็จให้วนซ้ำ ดีกว่าค้างเฟรมสุดท้ายนิ่ง ๆ
                            // (ไม่ใช้ isLooping=true เพราะแบบนั้น OnCompletion จะไม่ยิงเลย
                            //  แล้วเราจะไม่รู้ว่าคลิปเล่นครบรอบแล้วหรือยัง)
                            mp.setOnCompletionListener { p ->
                                onPlayedThrough()
                                if (shouldReplay()) {
                                    runCatching { p.seekTo(0); p.start() }
                                }
                            }
                            mp.setOnErrorListener { _, what, extra ->
                                Log.w("IntroSplash", "เล่นคลิปเปิดแอพไม่ได้ what=$what extra=$extra")
                                onFailed()
                                true
                            }
                            mp.prepareAsync()
                            holder[0] = mp
                        } catch (e: Exception) {
                            Log.w("IntroSplash", "เตรียมคลิปเปิดแอพไม่สำเร็จ", e)
                            onFailed()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        val p = holder[0] ?: return
                        applyCenterCrop(this@apply, p.videoWidth, p.videoHeight, w, h)
                    }

                    // ปล่อยตัวเล่นก่อนคืน surface — ถ้าปล่อยให้ MediaPlayer เขียนลง surface
                    // ที่ถูกทำลายไปแล้ว (เช่นผู้ใช้กดออกจากแอพกลางคลิป) จะเด้ง error ทิ้งไว้
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { holder[0]?.release() }
                        holder[0] = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        }
    )
}

/** ขยายคลิปให้เต็มจอโดยไม่บิดสัดส่วน แล้วครอปส่วนเกินทิ้งเท่า ๆ กันสองข้าง */
private fun applyCenterCrop(view: TextureView, videoW: Int, videoH: Int, viewW: Int, viewH: Int) {
    if (videoW <= 0 || videoH <= 0 || viewW <= 0 || viewH <= 0) return
    val scale = maxOf(viewW.toFloat() / videoW, viewH.toFloat() / videoH)
    val m = Matrix()
    m.setScale(
        videoW * scale / viewW,
        videoH * scale / viewH,
        viewW / 2f,
        viewH / 2f
    )
    view.setTransform(m)
}

/**
 * ฉากสำรองแบบภาพนิ่ง — โล่ + ตัวอักษรแบรนด์ บนพื้นไล่เฉดกรมท่า
 * ใช้ทั้งตอน "รอเฟรมแรกของคลิป" และตอน "เล่นคลิปไม่ได้"
 */
@Composable
private fun StaticIntro(hidden: Boolean) {
    val appear = remember { Animatable(0f) }
    val cover = remember { Animatable(1f) }

    LaunchedEffect(Unit) { appear.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
    LaunchedEffect(hidden) { if (hidden) cover.animateTo(0f, tween(260)) }

    if (cover.value <= 0f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(cover.value)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF16243E), SplashNavy, Color(0xFF060C18))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo_mark),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .scale(0.86f + 0.14f * appear.value)
                    .alpha(appear.value)
            )
            Spacer(Modifier.height(28.dp))
            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = "SMS Checker",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .alpha(appear.value)
            )
        }
    }
}
