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
    // val albumName = "動画" // todo: アルバムを選択できるようにする
    val albumName = "家族"

    var movieEnded = false
    var interrupted = false

    val showImage: MutableLiveData<Bitmap> by lazy { MutableLiveData<Bitmap>() }
    val startBrowser: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val startMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prepareMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }

    private val oauth = OAuth()
    private val api = GooglePhotoApi()

    private lateinit var mediaList: MediaList

    private fun interrupt() {
        interrupted = true
    }

    fun forward() {
        mediaList.next()
        interrupt()
    }
    fun rewind() {
        mediaList.prev()
        interrupt()
    }
    fun forwardMuch() {
        mediaList.nextDate()
        interrupt()
    }
    fun rewindMuch() {
        mediaList.prevDate()
        interrupt()
    }

    fun start() {
        viewModelScope.launch {
            // todo: 認証が必要な時は ブラウザ起動してOAuth認証する旨の案内画面とボタンを表示する
            if (oauth.isAuthorizeRequired()) {
                // 現時点では 「SilkBrowserでのOAuth認証からローカル起動したWebServerをコールバックして code を取得する」方式のみに対応
                // TVデバイス向けのOAuth認証方式は用意されているが、Google Photo API が対応していない
                oauth.authorizeWithBrowser { startBrowser.value = it }
            }
            api.setAccessToken(oauth.waitAccessToken())

            val albums = api.getAlbumList() // todo: アルバムがない場合の処理
            val albumId = albums.first { it.title == albumName }.id
            mediaList = MediaList(api.getAllMediaItems(albumId)) // todo: アルバムが空の場合の処理

            var waitForContents: suspend () -> MediaItem = { mediaList.next() }
            var item = mediaList.current()
            while (true) {
                val isVideo = MimeTypes.isVideo(item.mimeType)

                Log.i("SlideShow", "showing URL: ${item.mimeType} ${item.baseUrl}")
                if (isVideo) {
                    prepareMovie.value = item.baseUrl

                    item = waitForContents()

                    startMovie.value = ""
                    waitForContents = waitForMovie
                } else {
                    val bitmap = loadImageBitmap(item.baseUrl)

                    item = waitForContents()

                    showImage.value = bitmap // update image
                    waitForContents = waitForImage
                }
            }
        }
    }

    private val waitForMovie: suspend () -> MediaItem = {
        while (!movieEnded) { // FullscreenActivity 側で再生終了で movieEnded == true になる
            delay(100)
            if (interrupted) break
        }
        movieEnded  = false
        if (interrupted) {
            interrupted = false
            mediaList.current()
        } else mediaList.next()
    }

    private val waitForImage: suspend () -> MediaItem = {
        for (i in 0..29) {
            delay(100)
            if (interrupted) break
        }
        if (interrupted) {
            interrupted = false
            mediaList.current()
        } else mediaList.next()
    }

    private suspend fun loadImageBitmap(url: String): Bitmap = withContext(Dispatchers.IO){
        val bitmapStream = java.net.URL(url).openStream()
        val ba = bitmapStream.readBytes()
        bitmapStream.close()
//        Log.i("SlideShow", "read ${ba.size} bytes [0]:${ba[0]}")
        val bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.size)
        bitmap
    }
}