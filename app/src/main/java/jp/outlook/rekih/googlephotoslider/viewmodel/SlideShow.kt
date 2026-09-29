package jp.outlook.rekih.googlephotoslider.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.util.MimeTypes
import jp.outlook.rekih.googlephotoslider.data.ExternalContents
import jp.outlook.rekih.googlephotoslider.data.GooglePhotoApi
import jp.outlook.rekih.googlephotoslider.model.AlbumPlaylist
import jp.outlook.rekih.googlephotoslider.model.Content
import jp.outlook.rekih.googlephotoslider.model.ContentCache
import jp.outlook.rekih.googlephotoslider.model.MediaItem
import jp.outlook.rekih.googlephotoslider.model.Nav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class SlideShow : ViewModel() {

    // 画面に表示すべき状態
    sealed interface Screen {
        object Loading : Screen
        object Empty : Screen
        data class Image(val bitmap: Bitmap, val caption: String) : Screen
        data class Movie(val url: String) : Screen
    }

    private sealed interface Event {
        data class Move(val nav: Nav) : Event
        data class MovieEnded(val url: String) : Event
        object TogglePause : Event
    }

    val screen = MutableLiveData<Screen>(Screen.Loading)

    // 次に表示する動画。画像の表示中に Player に先読みさせる
    val upcomingMovie = MutableLiveData<String?>(null)

    val paused = MutableLiveData(false)

    private var albumId: String? = null
    private lateinit var playlist: AlbumPlaylist
    private val cache = ContentCache(
        viewModelScope,
        { ExternalContents.loadImageBitmap(it) },
        { MimeTypes.isVideo(it.mimeType) },
    )

    // 現在表示しているアイテムの index
    private var index = 0

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var job: Job? = null

    private val dateFormatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())

    fun forward() = send(Event.Move(Nav.NEXT))
    fun rewind() = send(Event.Move(Nav.PREV))
    fun forwardMuch() = send(Event.Move(Nav.NEXT_DATE))
    fun rewindMuch() = send(Event.Move(Nav.PREV_DATE))
    fun togglePause() = send(Event.TogglePause)
    fun notifyMovieEnded(url: String) = send(Event.MovieEnded(url))

    private fun send(event: Event) {
        events.trySend(event)
    }

    fun prepare(albumId: String) {
        // Activity 再生成時に同じアルバムの再生位置をリセットしない
        if (this.albumId == albumId) return
        this.albumId = albumId
        playlist = AlbumPlaylist(
            { GooglePhotoApi.searchMediaItems(albumId, it) },
            { GooglePhotoApi.batchGetMediaItems(it) },
        )
        viewModelScope.launch { playlist.loadAll() }
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

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runLoop() {
        while (true) {
            if (playlist.awaitItem(index) == null) {
                screen.value = Screen.Empty
                return
            }
            val indices = playlist.window(index)
            val window = playlist.freshen(indices)
            cache.retain(window)
            val content = cache.get(window.first())

            // 読み込み中に移動操作があった場合は、表示せずにそのまま移動する
            val pending = pendingMoves()
            if (pending.isNotEmpty()) {
                pending.forEach { index = playlist.move(index, it) }
                continue
            }

            val nextPos = playlist.state.value.move(index, Nav.NEXT)?.let { indices.indexOf(it) } ?: -1
            show(content, window.getOrNull(nextPos))
            index = playlist.move(index, waitNext(content))
        }
    }

    private fun show(content: Content, next: MediaItem?) {
        when (content) {
            is Content.Image -> screen.value =
                Screen.Image(content.bitmap, formatDate(content.item.mediaMetadata.creationTime))
            is Content.Movie -> screen.value = Screen.Movie(content.url)
            // 読み込めなかった場合は前の表示のまま
            is Content.Failed -> {}
        }
        upcomingMovie.value =
            if (content !is Content.Movie && next != null && MimeTypes.isVideo(next.mimeType)) {
                "${next.baseUrl}=dv"
            } else {
                null
            }
    }

    // 表示中のコンテンツの終了（画像は時間経過、動画は再生終了）か移動操作を待ち、次の移動方法を返す
    private suspend fun waitNext(content: Content): Nav {
        while (true) {
            val timeout = when {
                paused.value == true -> null
                content is Content.Image -> IMAGE_INTERVAL_MS
                content is Content.Failed -> FAILED_INTERVAL_MS
                else -> null
            }
            val event = if (timeout != null) {
                withTimeoutOrNull(timeout) { events.receive() } ?: return Nav.NEXT
            } else {
                events.receive()
            }
            when (event) {
                is Event.Move -> return event.nav
                is Event.MovieEnded -> if (content is Content.Movie && content.url == event.url) return Nav.NEXT
                is Event.TogglePause -> paused.value = paused.value != true
            }
        }
    }

    // 溜まっている移動操作を取り出す。一時停止の切り替えは適用し、動画終了は表示前なので捨てる
    private fun pendingMoves(): List<Nav> {
        val moves = mutableListOf<Nav>()
        while (true) {
            when (val event = events.tryReceive().getOrNull() ?: return moves) {
                is Event.Move -> moves += event.nav
                is Event.TogglePause -> paused.value = paused.value != true
                is Event.MovieEnded -> {}
            }
        }
    }

    private fun formatDate(creationTime: String): String = try {
        dateFormatter.format(Instant.parse(creationTime))
    } catch (e: DateTimeParseException) {
        ""
    }

    companion object {
        private const val IMAGE_INTERVAL_MS = 3000L
        private const val FAILED_INTERVAL_MS = 1000L
    }
}
