package jp.outlook.rekih.googlephotoslider.data

import android.util.Log
import jp.outlook.rekih.googlephotoslider.BuildConfig
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.net.InetAddress

const val CLIENT_ID = BuildConfig.googlePhotoApiClientId
const val CLIENT_SECRET = BuildConfig.googlePhotoApiClientSecret


object OAuth {
    private const val REDIRECT_HOST = "localhost"
    private const val REDIRECT_PORT = 5556
    private const val REDIRECT_URL = "http://$REDIRECT_HOST:$REDIRECT_PORT/oauth/callback"
    private const val SCOPE = "https://www.googleapis.com/auth/photoslibrary"
    private val BASE_URL = "https://accounts.google.com/o/oauth2/v2/auth".toHttpUrl()

    private var oAuthCode: String = ""
    private var accessToken: String = ""
    private var refreshToken: String = ""

    fun authorizeWithBrowser(browserInvoker: (String) -> Unit) {
        startWebServer()
        browserInvoker(createBrowserIntentUrl())
    }

    private fun createBrowserIntentUrl(): String {
        return BASE_URL
            .newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("redirect_uri", REDIRECT_URL)
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .build()
            .toString()
    }

    private fun parseCode(uri: String): String {
        val url = uri.toHttpUrlOrNull()
        if (url != null) {
            val code = url.queryParameter("code")
            if (code != null) {
                return code
            }
        }
        return ""
    }

    suspend fun isAuthorizeRequired(): Boolean {
        refreshToken = Preference.getRefreshToken()
        if (refreshToken == "") {
            return true
        }
        storeAccessTokenByRefreshToken()
        return false
    }

    suspend fun waitAccessToken(): String {
        while (accessToken == "") {
            delay(100)
            // todo: timeout/error handling
        }
        return accessToken
    }
    private suspend fun storeAccessTokenByRefreshToken() {
        val body = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("redirect_uri", REDIRECT_URL)
            .add("grant_type", "refresh_token")
            .build()
        val resp = getAccessToken(body)
        accessToken = resp.accessToken
    }

    private suspend fun storeAccessTokenByCode(code: String) {
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("redirect_uri", REDIRECT_URL)
            .add("grant_type", "authorization_code")
            .add("access_type", "offline")
            .add("prompt", "consent")
            .build()
        val resp = getAccessToken(body)
        Log.i("oauth", "got token:$resp")
        // todo: error handling
        accessToken = resp.accessToken
        if (resp.refreshToken.isNotEmpty()) {
            refreshToken = resp.refreshToken
            Preference.storeRefreshToken(refreshToken)
        }
    }

    @ExperimentalSerializationApi
    private suspend fun getAccessToken(body: FormBody): AuthResponse = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val tokenUrl = "https://www.googleapis.com/oauth2/v4/token"
        val response = client.newCall(Request.Builder().url(tokenUrl).post(body).build()).execute()
        val responseBody = response.body?.string() ?: ""
        Log.i("OAuth", "got token: $response $responseBody")
        val json = Json.decodeFromString<AuthResponse>(responseBody)
        json
    }

    private fun handleRequest(req: RecordedRequest): MockResponse {
        val url = req.requestUrl
        if (url != null) {
            Log.i("OAuth", "got url: ${url.toString()}")
            val path = url.pathSegments
            if (path == listOf("oauth","callback")) {
                oAuthCode = parseCode(url.toString())
                CoroutineScope(Dispatchers.IO).launch {
                    storeAccessTokenByCode(oAuthCode)
                }
            }
        }
        return MockResponse().apply {
            setResponseCode(200)
            setBody("got code: $oAuthCode. please back to app")
        }
    }

    private fun startWebServer() {
        CoroutineScope(Dispatchers.IO).launch {
            val server = MockWebServer()
            // SAMで書きたいが今のところ書けない
            // server.dispatcher = { request: RecordedRequest -> handleRequest(request) }
            server.dispatcher = object: Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return handleRequest(request)
                }
            }
            server.start(InetAddress.getByName(REDIRECT_HOST), REDIRECT_PORT)
        }
    }
}

// todo: inner class にする
@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in")val expiresIn: Int,
    @SerialName("scope")val scope: String,
    @SerialName("token_type")val tokenType: String,
)