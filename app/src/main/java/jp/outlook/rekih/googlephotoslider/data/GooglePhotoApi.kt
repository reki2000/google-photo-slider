package jp.outlook.rekih.googlephotoslider.data

import android.util.Log
import jp.outlook.rekih.googlephotoslider.model.Album
import jp.outlook.rekih.googlephotoslider.model.Albums
import jp.outlook.rekih.googlephotoslider.model.MediaItem
import jp.outlook.rekih.googlephotoslider.model.MediaItemResults
import jp.outlook.rekih.googlephotoslider.model.MediaItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object GooglePhotoApi {
    private val decoder = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    private val MEDIA_ITEMS_FETCH_SIZE = 100

    private var accessToken: String = ""

    private suspend fun waitAccessToken() {
        while (accessToken == "") {
            delay(100)
            // todo: timeout/error handling
        }
    }

    fun setAccessToken(token: String): GooglePhotoApi {
        accessToken = token
        return this
    }

    // アクセストークンは1時間で失効するため、401 の場合はリフレッシュして1回だけリトライする
    private suspend fun execute(buildRequest: (Request.Builder) -> Request.Builder): String {
        waitAccessToken()
        for (retry in 0..1) {
            val req = buildRequest(Request.Builder())
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.code == 401 && retry == 0) {
                    Log.i("api", "access token expired, refreshing")
                    accessToken = OAuth.refreshAccessToken()
                } else if (!response.isSuccessful) {
                    throw IOException("API error: $response")
                } else {
                    return response.body?.string() ?: ""
                }
            }
        }
        throw IOException("API error: unauthorized")
    }

    suspend fun getAlbumList(): List<Album> = withContext(Dispatchers.IO) {
        val url = "https://photoslibrary.googleapis.com/v1/albums".toHttpUrlOrNull()!!
        val albums = mutableListOf<Album>()
        var nextPageToken: String? = null
        do {
            val uriBuilder = url.newBuilder()
            nextPageToken?.let { uriBuilder.addQueryParameter("pageToken", it) }
            val responseBody = execute { it.url(uriBuilder.build()).get() }
            val json = decoder.decodeFromString<Albums>(responseBody)

            albums += json.albums
            nextPageToken = json.nextPageToken
        } while (nextPageToken != null)
        albums
    }

    // 最大50件
    suspend fun batchGetMediaItems(ids: List<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        val urlBuilder = "https://photoslibrary.googleapis.com/v1/mediaItems:batchGet".toHttpUrlOrNull()!!
            .newBuilder()
        ids.forEach { urlBuilder.addQueryParameter("mediaItemIds", it) }
        val url = urlBuilder.build()
        val responseBody = execute { it.url(url).get() }
        decoder.decodeFromString<MediaItemResults>(responseBody).mediaItemResults.mapNotNull { it.mediaItem }
    }

    suspend fun searchMediaItems(albumId: String, pageToken: String?): MediaItems = withContext(
        Dispatchers.IO
    ) {
        val url =
            "https://photoslibrary.googleapis.com/v1/mediaItems:search".toHttpUrlOrNull()!!
        val query = buildJsonObject {
            put("albumId", albumId)
            put("pageSize", MEDIA_ITEMS_FETCH_SIZE)
            pageToken?.let { put("pageToken", it) }
        }
        val body =
            query.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val responseBody = execute { it.url(url).post(body) }
        val json = decoder.decodeFromString<MediaItems>(responseBody)
        Log.i("Api", "got ${json.mediaItems.size} items, nextpagetoken ${json.nextPageToken}")
        json
    }
}
