package jp.outlook.rekih.googlephotoslider.model

class MediaList(val fetchNext: suspend () -> List<MediaItem>) {
    private var itemIndex = 0
    private var items = listOf<MediaItem>()

    companion object {
        private const val PREFETCH_LIMIT = 20
    }

    suspend fun current(): MediaItem {
        if (itemIndex >= items.size - PREFETCH_LIMIT) {
            items += fetchNext()
        }
        return items[itemIndex]
    }

    suspend fun prevMonth(): MediaItem {
        val date = prev().mediaMetadata.creationTime.substring(0, 7)
        while (itemIndex != 0) {
            val item = prev()
            if (item.mediaMetadata.creationTime.substring(0, 7) != date) {
                break
            }
        }
        return current()
    }

    suspend fun nextMonth(): MediaItem {
        val date = current().mediaMetadata.creationTime.substring(0, 7)
        while (itemIndex != 0) {
            val item = next()
            if (item.mediaMetadata.creationTime.substring(0, 7) != date) {
                break
            }
        }
        return current()
    }


    suspend fun prevDate(): MediaItem {
        val date = prev().mediaMetadata.creationTime.substring(0, 10)
        while (itemIndex != 0) {
            val item = prev()
            if (item.mediaMetadata.creationTime.substring(0, 10) != date) {
                break
            }
        }
        return current()
    }

    suspend fun nextDate() : MediaItem {
        val date = current().mediaMetadata.creationTime.substring(0,10)
        while (itemIndex != 0) {
            val item = next()
            if (item.mediaMetadata.creationTime.substring(0,10) != date) {
                break
            }
        }
        return current()
    }

    suspend fun prev() : MediaItem {
        if (itemIndex > 0) {
            itemIndex -= 1
        } else {
            itemIndex = items.size - 1
        }
        return current()
    }

    suspend fun next() : MediaItem {
        if (itemIndex >= items.size - 1) {
            itemIndex = 0
        } else {
            itemIndex += 1
        }
        return current()
    }
}