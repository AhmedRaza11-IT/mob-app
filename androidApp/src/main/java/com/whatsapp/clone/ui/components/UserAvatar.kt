package com.whatsapp.clone.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.whatsapp.clone.WaGreenPrimary
import com.whatsapp.clone.config.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object AvatarCache {
    private val memoryCache = LruCache<String, Bitmap>(60)

    fun get(url: String): Bitmap? = memoryCache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        memoryCache.put(url, bitmap)
    }

    fun remove(url: String) {
        memoryCache.remove(url)
    }

    fun clear() {
        memoryCache.evictAll()
    }
}

@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    fallbackBackgroundColor: Color = WaGreenPrimary,
    fallbackTextColor: Color = Color.White,
    fontSize: TextUnit = 18.sp
) {
    val fullUrl = remember(avatarUrl) {
        when {
            avatarUrl.isNullOrBlank() -> null
            avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") -> avatarUrl
            else -> {
                val base = NetworkConfig.getBaseUrl().removeSuffix("/")
                val path = if (avatarUrl.startsWith("/")) avatarUrl else "/$avatarUrl"
                "$base$path"
            }
        }
    }

    var bitmap by remember(fullUrl) {
        mutableStateOf<Bitmap?>(fullUrl?.let { AvatarCache.get(it) })
    }

    LaunchedEffect(fullUrl) {
        if (fullUrl != null) {
            val cached = AvatarCache.get(fullUrl)
            if (cached != null) {
                bitmap = cached
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL(fullUrl)
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 4000
                            readTimeout = 4000
                            useCaches = true
                        }
                        if (conn.responseCode == 200) {
                            val bmp = BitmapFactory.decodeStream(conn.inputStream)
                            if (bmp != null) {
                                AvatarCache.put(fullUrl, bmp)
                                withContext(Dispatchers.Main) {
                                    bitmap = bmp
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            bitmap = null
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(fallbackBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = if (displayName.isNotBlank()) displayName.trim().take(1).uppercase() else "V"
            Text(
                text = initial,
                color = fallbackTextColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
