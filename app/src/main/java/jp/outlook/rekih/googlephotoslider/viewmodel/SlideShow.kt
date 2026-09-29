package jp.outlook.rekih.googlephotoslider.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.util.MimeTypes
import jp.outlook.rekih.googlephotoslider.data.ExternalContents
import jp.outlook.rekih.googlephotoslider.data.GooglePhotoApi
import jp.outlook.rekih.googlephotoslider.model.MediaItem
import jp.outlook.rekih.googlephotoslider.model.MediaList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class SlideShow : ViewModel() {
    private var albumId: String? = null
    private lateinit var mediaList: MediaList

    private var job: Job? = null

    // ユーザー操作による移動。null でなければ表示待ちを中断してこれを適用する
    private var command: (suspend () -> MediaItem?)? = null

    private var movieEnded = false

    val showImage: MutableLiveData<Pair<Bitmap, String>> by lazy { MutableLiveData<Pair<Bitmap, String>>() }
    val startMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prepareMovie: MutableLiveData<String> by lazy { MutableLiveData<String>() }

    private val dateFormatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())

    fun notifyMovieEnded() {
        movieEnded = true
    }

    private fun enqueue(move: suspend () -> MediaItem?) {
        val prev = command
        command = {
            prev?.invoke()
            move()
        }
    }

    fun forward() = enqueue { mediaList.next() }

    fun rewind() = enqueue { mediaList.prev() }

    fun forwardMuch() = enqueue { mediaList.nextDate() }

    fun rewindMuch() = enqueue { mediaList.prevDate() }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun resume() = start()

    fun prepare(albumId: String) {
        // Activity 再生成時に同じアルバムの再生位置をリセットしない
        if (this.albumId == albumId) return
        this.albumId = albumId
        GooglePhotoApi.switchAlbumTo(albumId)
        mediaList = MediaList({ GooglePhotoApi.getNextMediaItems() }, { GooglePhotoApi.getMediaItem(it) })
    }

    fun start() {
        if (albumId == null || job?.isActive == true) return
        job = viewModelScope.launch {
            while (isActive) {
                try {
                    runLoop()
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("SlideShow", "slide show error, retrying", e)
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun runLoop() {
        // todo: アルバムが空の場合の表示
        var item = mediaList.current() ?: return
        var bitmap = load(item)

        while (true) {
            val shown = show(item, bitmap)

            // 表示中に次のコンテンツを先読みする
            val nextItem = mediaList.peekNext() ?: return
            val nextBitmap = load(nextItem)

            if (shown) {
                waitUntilContentsEnded(item)
            } else {
                delay(500)
            }

            val cmd = command
            command = null
            item = (if (cmd != null) cmd() else mediaList.next()) ?: return
            bitmap = if (item.id == nextItem.id) nextBitmap else load(item)
        }
    }

    private fun isVideo(item: MediaItem) = MimeTypes.isVideo(item.mimeType)

    // 画像は Bitmap を返す。動画は Player に準備させて null を返す
    private suspend fun load(item: MediaItem): Bitmap? {
        Log.i("SlideShow", "loading URL: ${item.mimeType} ${item.baseUrl}")
        if (isVideo(item)) {
            prepareMovie.value = item.baseUrl
            return null
        }
        return try {
            ExternalContents.loadImageBitmap(item.baseUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SlideShow", "failed to load image: ${item.id}", e)
            null
        }
    }

    // 表示できなかった場合は false
    private fun show(item: MediaItem, bitmap: Bitmap?): Boolean {
        if (isVideo(item)) {
            movieEnded = false
            startMovie.value = item.baseUrl
            return true
        }
        if (bitmap == null) return false
        showImage.value = Pair(bitmap, formatDate(item.mediaMetadata.creationTime))
        return true
    }

    private suspend fun waitUntilContentsEnded(item: MediaItem) {
        if (isVideo(item)) {
            // SlideShowActivity 側で再生終了/エラー時に movieEnded == true になる
            while (!movieEnded && command == null) {
                delay(100)
            }
        } else {
            repeat(30) {
                if (command != null) return
                delay(100)
            }
        }
    }

    private fun formatDate(creationTime: String): String = try {
        dateFormatter.format(Instant.parse(creationTime))
    } catch (e: DateTimeParseException) {
        ""
    }
}
