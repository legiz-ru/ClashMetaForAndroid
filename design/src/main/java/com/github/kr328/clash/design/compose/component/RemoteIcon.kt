package com.github.kr328.clash.design.compose.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Async icon loader for profile/proxy-group icons supplied as `file://` paths or
 * remote URLs. Remote icons are cached both in memory (process lifetime) and on
 * disk, so a profile logo survives an app restart — it shows instantly from disk
 * instead of flashing the default icon while it re-downloads, and works offline.
 */
object IconLoader {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    suspend fun load(context: Context, url: String): Bitmap? {
        if (url.isEmpty()) return null
        cache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                if (url.startsWith("file://")) {
                    val file = File(url.removePrefix("file://"))
                    (if (file.exists()) BitmapFactory.decodeFile(file.path) else null)
                        ?.also { cache[url] = it }
                } else {
                    val dir = File(context.cacheDir, "remote_icons").apply { mkdirs() }
                    val cacheFile = File(dir, url.hashCode().toString())
                    if (cacheFile.exists()) {
                        BitmapFactory.decodeFile(cacheFile.path)?.let {
                            cache[url] = it
                            return@withContext it
                        }
                    }
                    val bytes = URL(url).openStream().use { it.readBytes() }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bitmap ->
                        runCatching { cacheFile.writeBytes(bytes) }
                        cache[url] = bitmap
                    }
                }
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
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = IconLoader.load(context, url)
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
