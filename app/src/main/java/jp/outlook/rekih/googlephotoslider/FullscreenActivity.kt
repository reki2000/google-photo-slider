package jp.outlook.rekih.googlephotoslider

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import jp.outlook.rekih.googlephotoslider.databinding.ActivityFullscreenBinding

class FullscreenActivity : AppCompatActivity() {

    private val slideShow : SlideShow by viewModels()

    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var player: SimpleExoPlayer

    private lateinit var nextImageView: ImageView
    private lateinit var currentImageView: ImageView

    // TODO: 縦長の写真のみ、設定値に基づいてズームして表示 ZOOM_FOR_PORTLAIT = 1.3 など
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Preference.init(applicationContext)

        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        currentImageView = binding.fullscreenContent
        nextImageView = binding.fullscreenContent2

        // 動画playerの生成
        player = SimpleExoPlayer
            .Builder(this.applicationContext)
            .setPauseAtEndOfMediaItems(true)
            .build()
        player.apply {
            addListener(object: Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) {
                        slideShow.movieEnded = true
                    }
                }})
            // todo: 初回動画再生に時間がかからないようにする（初期化をする？）
            stop()
            clearMediaItems()
        }
        binding.playerView.useController = false
        binding.playerView.player = player

        // viewModelの変更を監視（本質はSlideShowからのUIに対するコマンドの受信）
        slideShow.startBrowser.observe(this, startBrowser)
        slideShow.showImage.observe(this, showImage)
        slideShow.startMovie.observe(this, startMovie)
        slideShow.prepareMovie.observe(this, prepareMovie)

        // スプラッシュ画像消去
        //binding.fullscreenContent.setImageResource(0)

        // スライドショー開始
        slideShow.prepare()
        slideShow.start()
    }

    override fun onStop() {
        super.onStop()
        player.stop()
        slideShow.stop()
    }

    override fun onRestart() {
        super.onRestart()
        hideSystemUI()
        slideShow.start()
    }

    private fun hideSystemUI() {
        val activityFullscreen = binding.root
        activityFullscreen.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

    }

    override fun onKeyDown(keyCode:Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                slideShow.forward()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                slideShow.forwardMuch()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                slideShow.rewind()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                slideShow.rewindMuch()
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private val startBrowser = Observer<String> { url ->
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private val showImage = Observer<Pair<Bitmap,String>> { (bitmap, caption) ->
        hideSystemUI()
        binding.playerView.visibility = View.INVISIBLE
        player.stop()

        binding.dateText.text = caption
        nextImageView.apply {
            setImageBitmap(bitmap)
            alpha = 0f
            animate().alpha(1f).setDuration(500);
        }
        currentImageView.apply {
            alpha = 1f
            animate().alpha(0f).setDuration(500);
        }
        binding.dateText.visibility = View.VISIBLE
        nextImageView.visibility = View.VISIBLE
        currentImageView.visibility = View.VISIBLE

        // switch imageview
        val tmpImageView = nextImageView
        nextImageView = currentImageView
        currentImageView = tmpImageView
    }

    private val prepareMovie = Observer<String> { uri ->
        val media = com.google.android.exoplayer2.MediaItem.fromUri("${uri}=dv")
        player.apply {
            addMediaItem(media)
            prepare()
        }
    }

    private val startMovie = Observer<String> { _ ->
        hideSystemUI()
        binding.fullscreenContent.visibility = View.INVISIBLE
        binding.fullscreenContent2.visibility = View.INVISIBLE
        binding.dateText.visibility = View.INVISIBLE
        currentImageView.setImageResource(0)
        nextImageView.setImageResource(0)

        binding.playerView.visibility = View.VISIBLE
        player.apply {
            seekToNextWindow()
            play()
        }
    }
}
