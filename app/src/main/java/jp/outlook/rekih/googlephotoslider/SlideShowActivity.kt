package jp.outlook.rekih.googlephotoslider

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import jp.outlook.rekih.googlephotoslider.databinding.ActivitySlideShowBinding
import jp.outlook.rekih.googlephotoslider.viewmodel.SlideShow
import jp.outlook.rekih.googlephotoslider.viewmodel.SlideShow.Screen

const val EXTRA_ALBUM_ID = "jp.outlook.rekih.googlephotoslider.EXTRA_ALBUM_ID"

class SlideShowActivity : AppCompatActivity() {

    private val slideShow: SlideShow by viewModels()

    private lateinit var binding: ActivitySlideShowBinding
    private lateinit var player: SimpleExoPlayer

    private lateinit var nextImageView: ImageView
    private lateinit var currentImageView: ImageView

    // Player にセット済みの動画の URL と、それを表示中かどうか
    private var preparedUrl: String? = null
    private var showingMovie = false

    private var lastPaused: Boolean? = null

    // TODO: 縦長の写真のみ、設定値に基づいてズームして表示 ZOOM_FOR_PORTLAIT = 1.3 など
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding = ActivitySlideShowBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        currentImageView = binding.fullscreenContent
        nextImageView = binding.fullscreenContent2

        // 動画playerの生成
        player = SimpleExoPlayer.Builder(this.applicationContext).build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    preparedUrl?.let { slideShow.notifyMovieEnded(it) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("SlideShow", "movie playback error", error)
                preparedUrl?.let { slideShow.notifyMovieEnded(it) }
            }
        })
        binding.playerView.useController = false
        binding.playerView.player = player

        // ViewModel の状態を描画する（画面を作り直した場合も最新の状態から描画できる）
        slideShow.screen.observe(this) { render(it) }
        slideShow.upcomingMovie.observe(this) { url ->
            // 画像の表示中に次の動画を読み込んでおく
            if (url != null && !showingMovie) {
                prepareMovie(url)
                player.playWhenReady = false
            }
        }
        slideShow.paused.observe(this) { paused ->
            if (showingMovie) {
                player.playWhenReady = !paused
            }
            if (lastPaused != null && lastPaused != paused) {
                Toast.makeText(this, if (paused) "一時停止" else "再開", Toast.LENGTH_SHORT).show()
            }
            lastPaused = paused
        }

        val albumId = intent.getStringExtra(EXTRA_ALBUM_ID)
        if (albumId != null) {
            slideShow.prepare(albumId)
        }
    }

    override fun onStart() {
        super.onStart()
        hideSystemUI()
        slideShow.start()
    }

    override fun onStop() {
        super.onStop()
        stopMovie()
        // 先読みしただけの動画も破棄する
        player.stop()
        preparedUrl = null
        slideShow.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    private fun hideSystemUI() {
        binding.root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun render(screen: Screen) {
        hideSystemUI()
        when (screen) {
            is Screen.Loading -> {}
            is Screen.Empty -> {
                stopMovie()
                binding.dateText.text = "表示できる写真がありません"
                binding.dateText.visibility = View.VISIBLE
            }
            is Screen.Image -> showImage(screen)
            is Screen.Movie -> startMovie(screen.url)
        }
    }

    private fun showImage(screen: Screen.Image) {
        stopMovie()
        binding.playerView.visibility = View.INVISIBLE

        binding.dateText.text = screen.caption

        nextImageView.apply {
            setImageBitmap(screen.bitmap)
            alpha = 0f
            animate().alpha(1f).setDuration(500)
        }
        currentImageView.apply {
            alpha = 1f
            animate().alpha(0f).setDuration(500)
        }

        binding.dateText.visibility = View.VISIBLE
        nextImageView.visibility = View.VISIBLE
        currentImageView.visibility = View.VISIBLE

        // switch imageview
        val tmpImageView = nextImageView
        nextImageView = currentImageView
        currentImageView = tmpImageView
    }

    private fun prepareMovie(url: String) {
        if (preparedUrl == url && player.playbackState != Player.STATE_IDLE) return
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        preparedUrl = url
    }

    private fun startMovie(url: String) {
        binding.fullscreenContent.visibility = View.INVISIBLE
        binding.fullscreenContent2.visibility = View.INVISIBLE
        currentImageView.setImageResource(0)

        binding.dateText.visibility = View.INVISIBLE
        binding.playerView.visibility = View.VISIBLE

        // 先読み済みならそのまま、そうでなければ読み込んでから先頭から再生する
        prepareMovie(url)
        player.seekToDefaultPosition()
        player.playWhenReady = slideShow.paused.value != true
        showingMovie = true
    }

    private fun stopMovie() {
        if (!showingMovie) return
        player.stop()
        showingMovie = false
        preparedUrl = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                slideShow.togglePause()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
