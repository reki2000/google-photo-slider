package jp.outlook.rekih.googlephotoslider

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InitActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Preference.init(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            if (OAuth.isAuthorizeRequired()) {
                // 現時点では 「SilkBrowserでのOAuth認証からローカル起動したWebServerをコールバックして code を取得する」方式のみに対応
                // TVデバイス向けのOAuth認証方式は用意されているが、Google Photo API が対応していない
                OAuth.authorizeWithBrowser {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                }
            }
            GooglePhotoApi.setAccessToken(OAuth.waitAccessToken())
        }

        startActivity(Intent(application, AlbumSelectActivity::class.java))
    }
}