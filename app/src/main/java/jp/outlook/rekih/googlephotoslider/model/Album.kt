package jp.outlook.rekih.googlephotoslider.model

import kotlinx.serialization.Serializable


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
)

@Serializable
data class MediaMetadata(
    val creationTime: String = "",
)