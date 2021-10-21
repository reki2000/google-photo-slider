package jp.outlook.rekih.googlephotoslider

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Preference {
    private lateinit var prefs: SharedPreferences

    private const val REFRESH_TOKEN_PREF_KEY = "refresh_token"
    private const val PREF_FILE = "entrypted_prefs"

    fun init(context: Context) {
        val mainKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            mainKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun storeRefreshToken(token: String) {
        prefs.edit().apply() {
            putString(REFRESH_TOKEN_PREF_KEY, token)
            apply()
        }
        Log.i("Preference", "stored token $token")
    }

    fun getRefreshToken(): String {
        val refreshToken = prefs.getString(REFRESH_TOKEN_PREF_KEY, "") ?: ""
        Log.i("Preference", "stored token ${refreshToken}")
        return refreshToken
    }
}