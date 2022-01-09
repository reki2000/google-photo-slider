package jp.outlook.rekih.googlephotoslider.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.util.MimeTypes
import jp.outlook.rekih.googlephotoslider.data.ExternalContents
import jp.outlook.rekih.googlephotoslider.data.GooglePhotoApi
import jp.outlook.rekih.googlephotoslider.model.MediaList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SlideShow : ViewModel() {
    private var interrupted = false
    private var running = false
    private var prepared = false

    private lateinit var mediaList: MediaList

    var movieEnded = false

    val showImage: MutableLiveData<Pair<Bitmap, String>> by lazy { MutableLiveData<Pair<Bitmap, String>>() }
    val startMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prepareMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }

    private val iso8601DateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").apply { timeZone = TimeZone.getDefault() }
    private val japaneseDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm")

    fun notifyMovieEnded() {
        movieEnded = true
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

    fun resume() {
        running = true
    }

    // 何らかの別イベントが割り込んだため、次コンテンツ待ちを中断してすぐに次のコンテンツに進む
    private fun interrupt() {
        interrupted = true
    }

    fun prepare(albumId: String) {
        viewModelScope.launch {
            GooglePhotoApi.switchAlbumTo(albumId)
            mediaList = MediaList { GooglePhotoApi.getNextMediaItems() }
            // todo: アルバムが空の場合の処理
        }
        prepared = true
    }

    fun start() {
        viewModelScope.launch {
            while (!prepared) {
                delay(100)
            }
            running = true

            var waitUntilContentsEnded = suspend { mediaList.next() }
            var item = mediaList.current()

            while (running) {
                val isVideo = MimeTypes.isVideo(item.mimeType)

                Log.i("SlideShow", "showing URL: ${item.mimeType} ${item.baseUrl}")
                if (isVideo) {
                    prepareMovie.value = item.baseUrl

                    item = waitUntilContentsEnded()

                    startMovie.value = ""
                    waitUntilContentsEnded = waitUntilMovieEnded
                } else {
                    val bitmap = ExternalContents.loadImageBitmap(item.baseUrl)

                    item = waitUntilContentsEnded()

                    val dateText = iso8601DateFormat.parse(item.mediaMetadata.creationTime)
                        ?.let { japaneseDateFormat.format(it) } ?: ""
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
        movieEnded = false
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