package jp.outlook.rekih.googlephotoslider.model

import android.graphics.Bitmap
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
    @Transient var coverPhotoBitmap: Bitmap? = null
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