package jp.outlook.rekih.googlephotoslider.model

class MediaList(
    val fetchNext: suspend () -> List<MediaItem>,
    val fetchOne: suspend (String) -> MediaItem,
) {
    private var itemIndex = 0
    private val PREFETCH_LIMIT = 20
    private val items = mutableListOf<MediaItem>()

    private suspend fun ensureLoaded() {
        if (itemIndex >= items.size - PREFETCH_LIMIT) {
            items += fetchNext()
        }
    }

    // baseUrl は約60分で失効するため、古くなったアイテムは取得し直す
    private suspend fun itemAt(index: Int): MediaItem? {
        val item = items.getOrNull(index) ?: return null
        if (!item.isExpired()) {
            return item
        }
        val fresh = fetchOne(item.id)
        items[index] = fresh
        return fresh
    }

    private fun nextIndex() = if (itemIndex >= items.size - 1) 0 else itemIndex + 1

    // アルバムが空の場合は null
    suspend fun current(): MediaItem? {
        ensureLoaded()
        return itemAt(itemIndex)
    }

    suspend fun peekNext(): MediaItem? {
        ensureLoaded()
        return itemAt(nextIndex())
    }

    // 1つ前の日付のグループの先頭へ移動する
    suspend fun prevDate(): MediaItem? {
        val date = current()?.date() ?: return null
        var i = itemIndex
        // 現在の日付のグループの先頭
        while (i > 0 && items[i - 1].date() == date) i--
        if (i > 0) {
            val prevDate = items[i - 1].date()
            i--
            while (i > 0 && items[i - 1].date() == prevDate) i--
        }
        itemIndex = i
        return current()
    }

    // 次の日付のグループの先頭へ移動する（末尾まで行ったら先頭で止まる）
    suspend fun nextDate(): MediaItem? {
        val date = current()?.date() ?: return null
        while (true) {
            val item = next() ?: return null
            if (itemIndex == 0 || item.date() != date) {
                return item
            }
        }
    }

    suspend fun prev(): MediaItem? {
        ensureLoaded()
        itemIndex = if (itemIndex > 0) itemIndex - 1 else maxOf(items.size - 1, 0)
        return current()
    }

    suspend fun next(): MediaItem? {
        ensureLoaded()
        itemIndex = nextIndex()
        return current()
    }

    private fun MediaItem.date() = mediaMetadata.creationTime.take(10)
}
