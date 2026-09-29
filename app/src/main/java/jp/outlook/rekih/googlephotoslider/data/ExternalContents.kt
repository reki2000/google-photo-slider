package jp.outlook.rekih.googlephotoslider.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL

object ExternalContents {
    suspend fun loadImageBitmap(url: String): Bitmap = withContext(Dispatchers.IO){
        val ba = URL(url).openStream().use { it.readBytes() }
        BitmapFactory.decodeByteArray(ba, 0, ba.size)
            ?: throw IOException("failed to decode image: $url")
    }

}