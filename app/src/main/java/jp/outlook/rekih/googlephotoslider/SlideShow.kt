package jp.outlook.rekih.googlephotoslider

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SlideShow : ViewModel() {
    var movieEnded = false
    var running = true

    val showImage: MutableLiveData<Bitmap> by lazy { MutableLiveData<Bitmap>() }
    val startMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prepareMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }

    private val items = mutableListOf<MediaItem>()

    private val api = GooglePhotoApi

    fun stop() {
        running = false
    }

    fun start(albumId: String) {
        viewModelScope.launch {
            var waitForContents = suspend { }
            val waitForMovie = suspend {
                while (!movieEnded) { // FullscreenActivity 側で再生終了で true になる
                    delay(100)
                }
                movieEnded  = false
            }
            val waitForImage = suspend {
                delay(3000)
            }
            while (running) {
                val item = nextMediaItem(albumId)
                val isVideo = MimeTypes.isVideo(item.mimeType)

                Log.i("SlideShow", "showing URL: ${item.mimeType} ${item.baseUrl}")
                if (isVideo) {
                    prepareMovie.value = item.baseUrl

                    waitForContents()

                    startMovie.value = item.baseUrl
                    waitForContents = waitForMovie
                } else {
                    val bitmap = loadImageBitmap(item.baseUrl)

                    waitForContents()

                    showImage.value = bitmap // update image
                    waitForContents = waitForImage
                }
            }
        }
    }

    private suspend fun loadImageBitmap(url: String): Bitmap = withContext(Dispatchers.IO){
        val bitmapStream = java.net.URL(url).openStream()
        val ba = bitmapStream.readBytes()
        bitmapStream.close()
//        Log.i("SlideShow", "read ${ba.size} bytes [0]:${ba[0]}")
        val bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.size)
        bitmap
    }

    private suspend fun nextMediaItem(albumId: String) : MediaItem {
        if (items.isEmpty()) {
            val mediaItems = api.getNextMediaItems(albumId)
            items += mediaItems
        }
        return items.removeFirst()
    }
}