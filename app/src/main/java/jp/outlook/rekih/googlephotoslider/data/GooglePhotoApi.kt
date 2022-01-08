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
    private var accessToken: String = ""
    private val decoder = Json { ignoreUnknownKeys = true }

    private val MEDIA_ITEMS_FETCH_SIZE = 100
    private var nextPageToken: String? = null

    fun setAccessToken(token :String): GooglePhotoApi {
        accessToken = token
        Log.i("api","access token: $accessToken  this: $this")
        return this
    }

    suspend fun waitAccessToken() {
        while (accessToken == "") {
            delay(100)
            Log.i("api","waiting access token: $accessToken this: $this")
            // todo: timeout/error handling
        }
    }

    suspend fun getAlbumList(): List<Album> = withContext(Dispatchers.IO) {
        waitAccessToken()
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
            Log.i("api","request: $req")
            val response = client.newCall(req).execute()
            if (response.code == 200) {
                val responseBody = response.body?.string() ?: ""
    //            Log.i("OAuth", "got album: $response $responseBody")
                val json = Json.decodeFromString<Albums>(responseBody)

                albums += json.albums
                nextPageToken = json.nextPageToken
            } else {
                throw Error("API error: $response")
            }
        } while (nextPageToken != null)
        albums
    }

    suspend fun getNextMediaItems(albumId: String): List<MediaItem> = withContext(
        Dispatchers.IO
    ) {
        waitAccessToken()
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
        if (response.code != 200) {
            throw Error("api error: $response")
        }

        val responseBody = response.body?.string() ?: ""
//            Log.i("OAuth", "got mediaItems: $response $responseBody")
        val json = decoder.decodeFromString<MediaItems>(responseBody)

        mediaItems += json.mediaItems
        nextPageToken = json.nextPageToken
        Log.i("Api", "got ${json.mediaItems.size} items, nextpagetoken $nextPageToken")

        mediaItems
    }
}
