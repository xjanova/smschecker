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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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

/** เริ่มเล่นแล้วแต่ค้างกลางทาง — คลิปยาว 10 วิ เผื่อไว้อีกหน่อย */
private const val STUCK_MS = 14_000L

/**
 * หน้าเปิดแอพ — คลิปโลโก้สั้น ๆ 2.7 วินาที
 *
 * ทำไมใช้ TextureView ไม่ใช่ VideoView:
 *   VideoView วางบน SurfaceView ซึ่ง "เจาะรู" ทะลุ view ที่ซ้อนอยู่ → เอา Compose
 *   ไปวางทับแล้วลำดับชั้นเพี้ยน และจังหวะสร้าง surface มักแวบดำ
 *   TextureView เป็น view ปกติ ซ้อน/หรี่ความทึบ/ครอปด้วย matrix ได้ตามใจ
 *
 * ทางออกมี 4 ทาง (คลิปจบ / แตะข้าม / เล่นไม่ได้ / หมดเวลา) และเกิดพร้อมกันได้
 * เช่นแตะรัวตอนคลิปกำลังจบ → ต้องกันเรียก onFinished ซ้ำ ไม่งั้นสลับหน้าซ้อน
 */
@Composable
fun IntroSplashScreen(onFinished: () -> Unit) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val fired = remember { AtomicBoolean(false) }
    val finish: () -> Unit = { if (fired.compareAndSet(false, true)) latestOnFinished() }

    var videoDrawing by remember { mutableStateOf(false) }

    // นับถอยหลังเป็น 2 จังหวะ และ "รีสตาร์ทเมื่อวิดีโอเริ่มวาดจริง"
    //   จังหวะแรก  = รอดูว่าคลิปจะขึ้นไหม (เครื่องบางรุ่นถอดรหัสไม่ได้เลย)
    //   จังหวะสอง  = ขึ้นแล้ว ให้เวลาจนจบคลิป เผื่อเครื่องช้า
    // ⚠️ ต้องเริ่มนับหลัง withFrameNanos (= จอเราถูกวาดจริง) ไม่ใช่ตอน compose
    //    เพราะ cold start ระบบค้างที่ splash ของ Android ได้หลายวินาที
    //    (วัดบน emulator: `Displayed ... +4s926ms`) นับเร็วไปคลิปไม่ได้ออกอากาศเลย
    LaunchedEffect(videoDrawing) {
        withFrameNanos { }
        delay(if (videoDrawing) STUCK_MS else NO_VIDEO_MS)
        finish()
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
            onEnded = finish,
            onFailed = finish
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
    onEnded: () -> Unit,
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
                            mp.setOnCompletionListener { onEnded() }
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
