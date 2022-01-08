package jp.outlook.rekih.googlephotoslider.data

import android.util.Log
import jp.outlook.rekih.googlephotoslider.model.Album
import jp.outlook.rekih.googlephotoslider.model.Albums
import jp.outlook.rekih.googlephotoslider.model.MediaItem
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

object GooglePhotoApi {
    private val decoder = Json { ignoreUnknownKeys = true }

    private val MEDIA_ITEMS_FETCH_SIZE = 100

    private var accessToken: String = ""

    private var albumId = ""
    private var nextPageToken: String? = null
    private var completed = false

    private suspend fun waitAccessToken() {
        while (accessToken == "") {
            delay(100)
            Log.i("api", "waiting access token: $accessToken this: $this")
            // todo: timeout/error handling
        }
    }

    fun setAccessToken(token: String): GooglePhotoApi {
        accessToken = token
        Log.i("api", "access token: $accessToken  this: $this")
        return this
    }

    fun switchAlbumTo(id: String) {
        albumId = id
        nextPageToken = null
        completed = false
    }

    suspend fun getAlbumList(): List<Album> = withContext(Dispatchers.IO) {
        waitAccessToken()

        val url = "https://photoslibrary.googleapis.com/v1/albums".toHttpUrlOrNull()!!
        val albums = mutableListOf<Album>()
        var nextPageToken: String? = null
        do {
            val uriBuilder = url.newBuilder()
            nextPageToken?.let { uriBuilder.addQueryParameter("pageToken", it) }
            val req = Request.Builder()
                .url(uriBuilder.build())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            Log.i("api", "request: $req")

            val response = OkHttpClient().newCall(req).execute()

            if (response.code != 200) {
                throw Error("API error: $response")
            }

            val responseBody = response.body?.string() ?: ""
            val json = Json.decodeFromString<Albums>(responseBody)

            albums += json.albums
            nextPageToken = json.nextPageToken
        } while (nextPageToken != null)
        albums
    }

    suspend fun getNextMediaItems(): List<MediaItem> = withContext(
        Dispatchers.IO
    ) {
        waitAccessToken()

        val mediaItems = mutableListOf<MediaItem>()

        if (!completed) {
            val url =
                "https://photoslibrary.googleapis.com/v1/mediaItems:search".toHttpUrlOrNull()!!
            val query = buildJsonObject {
                put("albumId", albumId)
                put("pageSize", MEDIA_ITEMS_FETCH_SIZE)
                nextPageToken?.let { put("pageToken", it) }
            }
            val body =
                query.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            val response = OkHttpClient().newCall(req).execute()

            if (response.code != 200) {
                throw Error("api error: $response")
            }

            val responseBody = response.body?.string() ?: ""
//            Log.i("OAuth", "got mediaItems: $response $responseBody")
            val json = decoder.decodeFromString<MediaItems>(responseBody)

            mediaItems += json.mediaItems
            nextPageToken = json.nextPageToken
            if (nextPageToken == null) {
                completed = true
            }
            Log.i("Api", "got ${json.mediaItems.size} items, nextpagetoken ${nextPageToken}")
        }
        mediaItems
    }
}
