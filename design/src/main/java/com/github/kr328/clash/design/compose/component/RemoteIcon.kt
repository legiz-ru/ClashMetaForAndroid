package com.github.kr328.clash.design.compose.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal async icon loader (replaces MainDesign.loadIconAsync) for profile and
 * proxy-group icons supplied as `file://` paths or remote URLs. Results are cached
 * in memory for the process lifetime.
 */
object IconLoader {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    suspend fun load(url: String): Bitmap? {
        if (url.isEmpty()) return null
        cache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = if (url.startsWith("file://")) {
                    val file = File(url.removePrefix("file://"))
                    if (file.exists()) BitmapFactory.decodeFile(file.path) else null
                } else {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                }
                if (bitmap != null) cache[url] = bitmap
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Renders the image at [url] once loaded, showing [fallback] while loading or on
 * failure (or when [url] is empty).
 */
@Composable
fun RemoteIcon(
    url: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = IconLoader.load(url)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        fallback()
    }
}
