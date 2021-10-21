package jp.outlook.rekih.googlephotoslider

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GooglePhotoApi {
    private var accessToken: String = ""
    private val decoder = Json { ignoreUnknownKeys = true }

    private val MEDIA_ITEMS_FETCH_SIZE = 100
    private var nextPageToken: String? = null

    fun setAccessToken(token :String): GooglePhotoApi {
        accessToken = token
        return this
    }

    suspend fun getAlbumList(): List<Album> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val url = "https://photoslibrary.googleapis.com/v1/albums".toHttpUrlOrNull()!!
        val albums = mutableListOf<Album>()
        var nextPageToken: String? = null
        do {
            val uriBuilder = url.newBuilder()
            if (nextPageToken != null) {
                uriBuilder.addQueryParameter("pageToken", nextPageToken)
            }
            val req = Request.Builder()
                .url(uriBuilder.build())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val response = client.newCall(req).execute()

            val responseBody = response.body?.string() ?: ""
//            Log.i("OAuth", "got album: $response $responseBody")
            val json = Json.decodeFromString<Albums>(responseBody)

            albums += json.albums
            nextPageToken = json.nextPageToken
        } while (nextPageToken != null)
        albums
    }

    suspend fun getNextMediaItems(albumId: String): List<MediaItem> = withContext(
        Dispatchers.IO
    ) {
        val client = OkHttpClient()
        val url = "https://photoslibrary.googleapis.com/v1/mediaItems:search".toHttpUrlOrNull()!!
        val mediaItems = mutableListOf<MediaItem>()

        val query = buildJsonObject {
            put("albumId", albumId)
            put("pageSize", MEDIA_ITEMS_FETCH_SIZE)
            if (nextPageToken != null) {
                put("pageToken", nextPageToken)
            }
        }
        val body =
            query.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        val response = client.newCall(req).execute()

        val responseBody = response.body?.string() ?: ""
//            Log.i("OAuth", "got mediaItems: $response $responseBody")
        val json = decoder.decodeFromString<MediaItems>(responseBody)

        mediaItems += json.mediaItems
        nextPageToken = json.nextPageToken
        Log.i("Api", "got ${json.mediaItems.size} items, nextpagetoken ${nextPageToken}")

        mediaItems
    }
}

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
)