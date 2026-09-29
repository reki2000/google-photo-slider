package jp.outlook.rekih.googlephotoslider.model

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

sealed interface Content {
    val item: MediaItem

    data class Image(override val item: MediaItem, val bitmap: Bitmap) : Content
    data class Movie(override val item: MediaItem, val url: String) : Content
    data class Failed(override val item: MediaItem) : Content
}

// 表示付近のコンテンツを並行して先読みして保持する
class ContentCache(
    private val scope: CoroutineScope,
    private val loadBitmap: suspend (url: String) -> Bitmap,
    private val isVideo: (MediaItem) -> Boolean,
) {
    private val entries = mutableMapOf<String, Pair<MediaItem, Deferred<Content>>>()

    // items 以外は破棄（読み込み中ならキャンセル）し、items のうち未取得のものを読み込み始める
    fun retain(items: List<MediaItem>) {
        val keep = items.map { it.id }.toSet()
        entries.keys.filter { it !in keep }.forEach { id ->
            entries.remove(id)?.second?.cancel()
        }
        items.forEach { ensure(it) }
    }

    suspend fun get(item: MediaItem): Content {
        ensure(item)
        return entries.getValue(item.id).second.await()
    }

    private fun ensure(item: MediaItem) {
        val entry = entries[item.id]
        // baseUrl が更新されていたら読み込み直す（動画は再生時に URL を使うため）
        if (entry != null && entry.first.baseUrl == item.baseUrl) return
        entry?.second?.cancel()
        entries[item.id] = item to scope.async { load(item) }
    }

    private suspend fun load(item: MediaItem): Content {
        if (isVideo(item)) {
            return Content.Movie(item, "${item.baseUrl}=dv")
        }
        return try {
            Content.Image(item, loadBitmap(item.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ContentCache", "failed to load image: ${item.id}", e)
            Content.Failed(item)
        }
    }
}
