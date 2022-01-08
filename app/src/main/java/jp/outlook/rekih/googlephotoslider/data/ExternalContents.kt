package jp.outlook.rekih.googlephotoslider.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object ExternalContents {
    suspend fun loadImageBitmap(url: String): Bitmap = withContext(Dispatchers.IO){
        val bitmapStream = URL(url).openStream()
        val ba = bitmapStream.readBytes()
        bitmapStream.close()
//        Log.i("SlideShow", "read ${ba.size} bytes [0]:${ba[0]}")
        val bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.size)
        bitmap
    }

}