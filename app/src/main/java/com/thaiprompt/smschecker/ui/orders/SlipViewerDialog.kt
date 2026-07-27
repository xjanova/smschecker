package com.thaiprompt.smschecker.ui.orders

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.repository.SlipImageLoader
import com.thaiprompt.smschecker.ui.theme.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 🧾 (2026-07-27) จอดูสลิปที่ใช้อนุมัติบิล — เปิดจากการแตะการ์ดออเดอร์
 *
 * ให้แอดมินตรวจซ้ำด้วยตาว่า SlipOK อนุมัติถูกจริงไหม (สลิปปลอม/สลิปเก่า/ยอดไม่ตรง)
 * รูปโหลดสดจาก server ทุกครั้ง แคชแค่ในหน่วยความจำ (PDPA — ไม่เขียนลงเครื่อง)
 *
 * ซูมได้ด้วยการหุบ/กางนิ้ว · แตะสองครั้งเพื่อรีเซ็ตซูม
 *
 * @param thumbnail ทัมบ์เนลที่การ์ดโหลดไว้แล้ว — โชว์คั่นระหว่างรอรูปเต็ม (กันจอว่าง)
 */
@Composable
fun SlipViewerDialog(
    order: OrderApproval,
    thumbnail: Bitmap?,
    onLoadSlip: suspend (OrderApproval, Int) -> Bitmap?,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var fullImage by remember(order.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(order.id) { mutableStateOf(true) }

    LaunchedEffect(order.id, order.slipImagePath) {
        isLoading = true
        fullImage = onLoadSlip(order, SlipImageLoader.FULL_PX)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B1220))
        ) {
            // ── header: ชื่อจอ + เลขบิล + ปุ่มปิด ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = Color(0xFF8FD3FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        strings.slipViewerTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        "#${order.orderNumber ?: order.id}",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.slipCloseButton,
                        tint = Color.White
                    )
                }
            }

            // ── รูปสลิป (ซูม/เลื่อนได้) ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val shown = fullImage ?: thumbnail
                when {
                    shown != null -> ZoomableImage(bitmap = shown)
                    isLoading -> CircularProgressIndicator(
                        color = Color(0xFF8FD3FF),
                        strokeWidth = 3.dp
                    )
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            strings.slipLoadFailed,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ยังโหลดรูปเต็มไม่เสร็จแต่มีทัมบ์เนลโชว์อยู่ → spinner เล็กมุมขวาบน
                if (isLoading && shown != null) {
                    CircularProgressIndicator(
                        color = Color(0xFF8FD3FF),
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(20.dp)
                    )
                }
            }

            // ── ข้อมูลบนสลิปที่ SlipOK อ่านได้ ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp)
                    .verticalScroll(rememberScrollState())
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SlipInfoRow(strings.slipAmountLabel, order.slipAmount?.let {
                    String.format(Locale.US, "฿%,.2f", it)
                })
                SlipInfoRow(strings.slipSenderLabel, order.slipSenderName)
                SlipInfoRow(strings.slipReceiverLabel, order.slipReceiverAccount)
                SlipInfoRow(strings.slipTransRefLabel, order.slipTransRef)
                SlipInfoRow(strings.slipCheckedAtLabel, order.slipCheckedAt?.let { formatSlipTime(it) })
            }
        }
    }
}

/** รูปที่หุบ/กางนิ้วซูมได้ + ลากเลื่อนได้ · แตะสองครั้ง = รีเซ็ต */
@Composable
private fun ZoomableImage(bitmap: Bitmap) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = LocalAppStrings.current.slipViewerTitle,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
    )
}

/** แถวข้อมูลสลิป — ซ่อนทั้งแถวถ้าไม่มีค่า (SlipOK อ่านไม่ได้ทุกฟิลด์เสมอไป) */
@Composable
private fun SlipInfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 🧾 ทัมบ์เนลสลิปเล็กๆ บนการ์ดบิล — บอกว่า "บิลนี้มีสลิปให้ตรวจ" + แตะการ์ดเพื่อดูเต็ม
 * ระหว่างโหลดโชว์กรอบไอคอนแทน (ไม่ทำให้การ์ดกระตุก)
 */
@Composable
fun SlipThumbnail(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x1A2A3A52)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = LocalAppStrings.current.slipViewerTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.ReceiptLong,
                contentDescription = null,
                tint = Color(0x662A3A52),
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/**
 * รูปแบบเวลาเดียวกับการ์ดบิล — จงใจไม่ใส่ปี: SimpleDateFormat + locale ไทยจะเรนเดอร์เป็น
 * พ.ศ. ทำให้เทียบกับเวลาบน server (ค.ศ.) แล้วสับสน และสลิปเก็บแค่ 30 วันอยู่แล้ว
 */
private fun formatSlipTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(java.util.Date(timestamp))
