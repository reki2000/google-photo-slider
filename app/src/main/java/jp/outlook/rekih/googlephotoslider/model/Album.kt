package jp.outlook.rekih.googlephotoslider.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class Albums(
    val albums: List<Album> = listOf(),
    val nextPageToken: String? = null
)

@Serializable
data class Album(
    val id: String,
    val title: String,
    val productUrl: String,
    val mediaItemsCount: String = "",
    val coverPhotoBaseUrl: String = "",
    val coverPhotoMediaItemId: String = "",
)

@Serializable
data class MediaItems(
    val mediaItems: List<MediaItem> = listOf(),
    val nextPageToken: String? = null
)

@Serializable
data class MediaItem(
    val id: String,
    val productUrl: String,
    val baseUrl: String = "",
    val mimeType: String = "",
    val mediaMetadata: MediaMetadata,
    @Transient val fetchedAt: Long = System.currentTimeMillis(),
) {
    // baseUrl の有効期限は60分。余裕をもって50分で失効扱いにする
    fun isExpired(now: Long = System.currentTimeMillis()) = now - fetchedAt > 50 * 60 * 1000L
}

@Serializable
data class MediaItemResults(
    val mediaItemResults: List<MediaItemResult> = listOf(),
)

// 取得に失敗したアイテムは mediaItem が無く status が入る
@Serializable
data class MediaItemResult(
    val mediaItem: MediaItem? = null,
)

@Serializable
data class MediaMetadata(
    val creationTime: String = "",
)