package com.thaiprompt.smschecker.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.thaiprompt.smschecker.data.api.ApiClientFactory
import com.thaiprompt.smschecker.data.db.ServerConfigDao
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🧾 (2026-07-27) โหลดรูปสลิป SlipOK ของบิล — ทัมบ์เนลบนการ์ด + รูปเต็มตอนกดดู
 *
 * PDPA: รูปมีชื่อผู้โอน/เลขบัญชีลูกค้า → **แคชในหน่วยความจำเท่านั้น ไม่เขียนลงดิสก์**
 * (ปิดแอพ = หายหมด) และทุก request ต้องแนบ X-Api-Key + X-Device-Id ของ server นั้น
 *
 * รูปฝั่ง server ถูก purge ทุก 30 วัน → บิลเก่าจะได้ 404 → คืน null (การ์ดไม่โชว์ทัมบ์เนล)
 */
@Singleton
class SlipImageLoader @Inject constructor(
    private val serverConfigDao: ServerConfigDao,
    private val secureStorage: SecureStorage,
    private val apiClientFactory: ApiClientFactory
) {
    companion object {
        private const val TAG = "SlipImageLoader"

        /** ขนาดทัมบ์เนลบนการ์ด (px) — เล็กพอให้ scroll ลื่นแม้บิลเยอะ */
        const val THUMB_PX = 240

        /** ขนาดรูปเต็มตอนเปิดดู (px) — พออ่านเลขบัญชี/ยอดได้ ไม่กิน RAM เกิน */
        const val FULL_PX = 1600

        /** เพดานแคชรูป ~16MB (สลิปเต็ม 1600px ≈ 4-6MB, ทัมบ์เนล ≈ 100KB) */
        private const val CACHE_KB = 16 * 1024

        /** ขนาดไฟล์สูงสุดที่ยอมโหลด (5MB) — กัน response แปลกปลอมกิน RAM */
        private const val MAX_BYTES = 5 * 1024 * 1024L
    }

    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** ล็อกต่อ key — กันการ์ดหลายใบ/รูปเดียวกันยิงซ้ำพร้อมกัน */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** key ที่โหลดแล้วไม่เจอรูป (404/purge) — ไม่ต้องยิงซ้ำทุกครั้งที่ scroll ผ่าน */
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /**
     * โหลดรูปสลิปของบิล (แคชไว้ใน RAM)
     *
     * @param maxSizePx ด้านที่ยาวที่สุดของ bitmap ที่ต้องการ (THUMB_PX / FULL_PX)
     * @return null ถ้าบิลไม่มีสลิป / รูปถูก purge / โหลดไม่สำเร็จ
     */
    suspend fun load(order: OrderApproval, maxSizePx: Int): Bitmap? {
        val path = order.slipImagePath?.takeIf { it.isNotBlank() } ?: return null
        val key = "${order.serverId}|$path|$maxSizePx"

        cache.get(key)?.let { return it }
        if (missing.contains(key)) return null

        val lock = locks.getOrPut(key) { Mutex() }
        return lock.withLock {
            // อาจมีคนอื่นโหลดเสร็จระหว่างรอ lock
            cache.get(key)?.let { return@withLock it }
            if (missing.contains(key)) return@withLock null

            val bitmap = fetchAndDecode(order, path, maxSizePx, key)
            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        }
    }

    /** ทิ้งแคชรูปของบิลนี้ (เช่นหลังยกเลิกการอนุมัติ / รีเฟรช) */
    fun evict(order: OrderApproval) {
        val path = order.slipImagePath ?: return
        for (size in listOf(THUMB_PX, FULL_PX)) {
            val key = "${order.serverId}|$path|$size"
            cache.remove(key)
            missing.remove(key)
        }
    }

    private suspend fun fetchAndDecode(
        order: OrderApproval,
        path: String,
        maxSizePx: Int,
        key: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        // 🔒 ยอมรับเฉพาะ path ของ endpoint สลิปที่เรารู้จัก — กัน server (หรือ response ที่ถูกแทรก)
        //    ชี้ API key + device id ไปที่อื่น หรือหลุดออกนอก endpoint ที่ตั้งใจ
        if (!path.startsWith("api/v1/sms-payment/orders/") || !path.endsWith("/slip-image")) {
            Log.w(TAG, "ปฏิเสธ slip path ที่ไม่รู้จัก: $path")
            missing.add(key)
            return@withContext null
        }

        val server = serverConfigDao.getById(order.serverId) ?: return@withContext null
        val apiKey = secureStorage.getApiKey(server.id) ?: return@withContext null
        val deviceId = secureStorage.getDeviceId() ?: return@withContext null

        val url = buildUrl(server.baseUrl, path)
        val bytes = try {
            val response = apiClientFactory.getClient(server.baseUrl)
                .getSlipImage(url = url, apiKey = apiKey, deviceId = deviceId)
            if (!response.isSuccessful) {
                // 404 = ไม่มีสลิป/ถูก purge แล้ว → จำไว้ ไม่ต้องยิงซ้ำ
                if (response.code() == 404) missing.add(key)
                Log.w(TAG, "slip image HTTP ${response.code()} for ${order.orderNumber}")
                response.errorBody()?.close()
                return@withContext null
            }
            val body = response.body() ?: return@withContext null
            body.use {
                if (it.contentLength() > MAX_BYTES) {
                    Log.w(TAG, "slip image too large (${it.contentLength()} bytes) — skipped")
                    return@withContext null
                }
                it.bytes()
            }
        } catch (e: Exception) {
            // เน็ตล่ม/timeout → ไม่ mark missing เพื่อให้ลองใหม่รอบหน้าได้
            Log.w(TAG, "slip image fetch failed for ${order.orderNumber}: ${e.message}")
            return@withContext null
        }

        if (bytes.isEmpty()) return@withContext null
        decodeScaled(bytes, maxSizePx)
    }

    /**
     * decode แบบย่อขนาดตั้งแต่ต้น (inSampleSize) — สลิปมือถือมัก 1080x2400
     * ถ้า decode เต็มความละเอียดแล้วค่อยย่อ = เปลือง RAM ~10MB ต่อรูป
     */
    private fun decodeScaled(bytes: ByteArray, maxSizePx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null

            var sample = 1
            while (longest / (sample * 2) >= maxSizePx) sample *= 2

            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } catch (e: Exception) {
            Log.w(TAG, "slip image decode failed: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "slip image decode OOM")
            null
        }
    }

    /** baseUrl (อาจไม่มี scheme/ท้าย /) + relative path จาก server → absolute URL */
    private fun buildUrl(baseUrl: String, path: String): String {
        var base = baseUrl.trim()
        if (!base.startsWith("http")) base = "https://$base"
        if (!base.endsWith("/")) base += "/"
        return base + path.removePrefix("/")
    }
}
