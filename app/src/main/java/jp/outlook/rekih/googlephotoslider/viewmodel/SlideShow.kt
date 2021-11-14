package jp.outlook.rekih.googlephotoslider.viewmodel

import android.graphics.Bitmap
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.util.MimeTypes
import jp.outlook.rekih.googlephotoslider.model.MediaList
import jp.outlook.rekih.googlephotoslider.repository.ExternalContents
import jp.outlook.rekih.googlephotoslider.repository.GoogleOAuthApi
import jp.outlook.rekih.googlephotoslider.repository.GooglePhotoApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SlideShow : ViewModel() {
    // val albumName = "動画" // todo: アルバムを選択できるようにする
    val albumName = "家族"

    var movieEnded = false
    var interrupted = false
    var running = false
    var ready = false

    val showImage: MutableLiveData<Pair<Bitmap,String>> by lazy { MutableLiveData<Pair<Bitmap,String>>() }
    val startBrowser: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val startMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prepareMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }

    private lateinit var mediaList: MediaList

    private fun interrupt() {
        interrupted = true
    }

    fun forward() {
        viewModelScope.launch {
            mediaList.next()
            interrupt()
        }
    }
    fun rewind() {
        viewModelScope.launch {
            mediaList.prev()
            interrupt()
        }
    }
    fun forwardMuch() {
        viewModelScope.launch {
            mediaList.nextDate()
            interrupt()
        }
    }
    fun rewindMuch() {
        viewModelScope.launch {
            mediaList.prevDate()
            interrupt()
        }
    }

    fun stop() {
        running = false
        interrupt()
    }

    fun prepare() {
        viewModelScope.launch {
            val oauth = GoogleOAuthApi
            val api = GooglePhotoApi

            // todo: 認証が必要な時は ブラウザ起動してOAuth認証する旨の案内画面とボタンを表示する
            if (oauth.isAuthorizeRequired()) {
                // 現時点では 「SilkBrowserでのOAuth認証からローカル起動したWebServerをコールバックして code を取得する」方式のみに対応
                // TVデバイス向けのOAuth認証方式は用意されているが、Google Photo API が対応していない
                oauth.authorizeWithBrowser { startBrowser.value = it }
            }
            api.setAccessToken(oauth.waitAccessToken())

            val albums = api.getAlbumList() // todo: アルバムがない場合の処理
            val albumId = albums.first { it.title == albumName }.id
            mediaList = MediaList({ api.getNextMediaItems(albumId) }) // todo: アルバムが空の場合の処理
            ready = true
        }
    }

    private val iso8601DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").apply { timeZone = TimeZone.getDefault() }
    private val japaneseDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm")

    fun start() {
        viewModelScope.launch {
            while (!ready) {
                delay(100)
            }
            running = true
            var waitUntilContentsEnded = suspend { mediaList.next() }
            var item = mediaList.current()
            while (running) {
                val isVideo = MimeTypes.isVideo(item.mimeType)

                Log.i("SlideShow", "preparing URL: ${item.mimeType} ${item.baseUrl}")
                if (isVideo) {
                    prepareMovie.value = item.baseUrl

                    item = waitUntilContentsEnded()

                    startMovie.value = ""
                    waitUntilContentsEnded = waitUntilMovieEnded
                } else {
                    val bitmap = ExternalContents.loadImageBitmap(item.baseUrl)

                    item = waitUntilContentsEnded()

                    val dateText = japaneseDateFormat.format(iso8601DateFormat.parse(item.mediaMetadata.creationTime))
                    showImage.value = Pair(bitmap, dateText) // update image
                    waitUntilContentsEnded = waitUntilImageEnded
                }
            }
        }
    }

    private val waitUntilMovieEnded = suspend {
        while (!movieEnded) { // FullscreenActivity 側で再生終了で movieEnded == true になる
            delay(100)
            if (interrupted) break
        }
        movieEnded  = false
        postWait()
    }

    private val waitUntilImageEnded = suspend {
        for (i in 0..29) {
            delay(100)
            if (interrupted) break
        }
        postWait()
    }

    private val postWait = suspend {
        if (interrupted) {
            interrupted = false
            mediaList.current()
        } else {
            mediaList.next()
        }
    }

}