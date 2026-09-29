package jp.outlook.rekih.googlephotoslider.model

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

// アルバムのアイテム一覧を表示とは独立にバックグラウンドで読み込み、移動先の解決と baseUrl の更新を行う
class AlbumPlaylist(
    private val fetchPage: suspend (pageToken: String?) -> MediaItems,
    private val fetchItems: suspend (ids: List<String>) -> List<MediaItem>,
) {
    private val _state = MutableStateFlow(PlaylistState())
    val state: StateFlow<PlaylistState> = _state

    // 全ページを最後まで読み込む。失敗したらリトライし続ける
    suspend fun loadAll() {
        var pageToken: String? = null
        do {
            val page = retrying { fetchPage(pageToken) }
            _state.update { it.copy(items = it.items + page.mediaItems) }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        _state.update { it.copy(complete = true) }
        Log.i("AlbumPlaylist", "loaded ${_state.value.items.size} items")
    }

    // index のアイテムが読み込まれるまで待つ。アルバムが空なら null
    suspend fun awaitItem(index: Int): MediaItem? =
        state.first { it.items.size > index || it.complete }.items.getOrNull(index)

    // 移動先が確定するまで読み込みを待ってから返す
    suspend fun move(from: Int, nav: Nav): Int =
        state.first { it.move(from, nav) != null }.move(from, nav)!!

    // 表示付近のアイテム（先頭が現在のアイテム）の index
    fun window(index: Int): List<Int> {
        val s = _state.value
        val next = s.move(index, Nav.NEXT)
        return listOfNotNull(index, next, next?.let { s.move(it, Nav.NEXT) }, s.move(index, Nav.PREV))
            .distinct()
    }

    // indices のアイテムを baseUrl が有効な状態で返す。
    // 失効したものがあれば、この先の失効済みアイテムもまとめて取り直す
    suspend fun freshen(indices: List<Int>): List<MediaItem> {
        val items = _state.value.items
        if (indices.none { items[it].isExpired() }) {
            return indices.map { items[it] }
        }
        val ids = (indices.asSequence() + (indices.first() until items.size).asSequence())
            .map { items[it] }
            .filter { it.isExpired() }
            .map { it.id }
            .distinct()
            .take(BATCH_GET_LIMIT)
            .toList()
        try {
            val fresh = fetchItems(ids).associateBy { it.id }
            _state.update { s -> s.copy(items = s.items.map { fresh[it.id] ?: it }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AlbumPlaylist", "failed to refresh items", e)
        }
        return indices.map { _state.value.items[it] }
    }

    private suspend fun <T> retrying(block: suspend () -> T): T {
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AlbumPlaylist", "failed to load, retrying", e)
                delay(10_000)
            }
        }
    }

    companion object {
        private const val BATCH_GET_LIMIT = 50
    }
}
