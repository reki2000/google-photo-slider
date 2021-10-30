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

    val showImage: MutableLiveData<Bitmap> by lazy { MutableLiveData<Bitmap>() }
    val startBrowser: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val startMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prepareMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val selectAlbum: MutableLiveData<List<String>> by lazy { MutableLiveData<List<String>>() }

    private val items = mutableListOf<MediaItem>()
    private val oauth = OAuth()
    private val api = GooglePhotoApi()

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
            while (true) {
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