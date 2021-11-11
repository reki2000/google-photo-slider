package jp.outlook.rekih.googlephotoslider

class MediaList(val items: List<MediaItem>) {
    private var itemIndex = 0

    fun current(): MediaItem {
        return items[itemIndex]
    }

    fun prevDate() : MediaItem {
        val date = prev().mediaMetadata.creationTime.substring(0,10)
        while (itemIndex != 0) {
            val item = prev()
            if (item.mediaMetadata.creationTime.substring(0,10) != date) {
                break
            }
        }
        return items[itemIndex]
    }

    fun nextDate() : MediaItem {
        val date = current().mediaMetadata.creationTime.substring(0,10)
        while (itemIndex != 0) {
            val item = next()
            if (item.mediaMetadata.creationTime.substring(0,10) != date) {
                break
            }
        }
        return items[itemIndex]
    }

    fun prev() : MediaItem {
        if (itemIndex > 0) {
            itemIndex -= 1
        } else {
            itemIndex = items.size - 1
        }
        return items[itemIndex]
    }

    fun next() : MediaItem {
        if (itemIndex >= items.size - 1) {
            itemIndex = 0
        } else {
            itemIndex += 1
        }
        return items[itemIndex]
    }
}