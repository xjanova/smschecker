package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A composable that displays a faint logo watermark behind the content.
 *
 * Note: To enable the watermark, add a `logo_watermark.webp` drawable resource
 * and uncomment the Image composable below.
 *
 * Usage:
 *   WatermarkBackground {
 *       // Your content here
 *   }
 */
@Composable
fun WatermarkBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // TODO: Uncomment when logo_watermark drawable is added
        // Image(
        //     painter = painterResource(id = R.drawable.logo_watermark),
        //     contentDescription = null,
        //     modifier = Modifier
        //         .fillMaxSize(0.6f)
        //         .align(Alignment.Center)
        //         .alpha(0.05f),
        //     contentScale = ContentScale.Fit
        // )
        content()
    }
}
