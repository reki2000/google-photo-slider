package jp.outlook.rekih.googlephotoslider

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import jp.outlook.rekih.googlephotoslider.databinding.ActivitySlideShowBinding
import jp.outlook.rekih.googlephotoslider.viewmodel.SlideShow

const val EXTRA_ALBUM_ID = "jp.outlook.rekih.googlephotoslider.EXTRA_ALBUM_ID"

class SlideShowActivity : AppCompatActivity() {

    private val slideShow: SlideShow by viewModels()

    private lateinit var binding: ActivitySlideShowBinding
    private lateinit var player: SimpleExoPlayer

    private lateinit var nextImageView: ImageView
    private lateinit var currentImageView: ImageView

    // TODO: 縦長の写真のみ、設定値に基づいてズームして表示 ZOOM_FOR_PORTLAIT = 1.3 など
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding = ActivitySlideShowBinding.inflate(layoutInflater)
        val activitySlideShow = binding.root
        setContentView(activitySlideShow)
        hideSystemUI()

        currentImageView = binding.fullscreenContent
        nextImageView = binding.fullscreenContent2

        // 動画playerの生成
        player = SimpleExoPlayer
            .Builder(this.applicationContext)
            .setPauseAtEndOfMediaItems(true)
            .build()
        player.apply {
            // isPlaying はバッファリングや stop() でも false になるため、終了判定には使わない
            addListener(object: Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                        slideShow.notifyMovieEnded()
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        slideShow.notifyMovieEnded()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w("SlideShow", "movie playback error", error)
                    slideShow.notifyMovieEnded()
                }
            })
            // todo: 初回動画再生に時間がかからないようにする（初期化をする？）
            stop()
            clearMediaItems()
        }
        binding.playerView.useController = false
        binding.playerView.player = player

        // viewModelの変更を監視（本質はSlideShowからのUIに対するコマンドの受信）
        slideShow.showImage.observe(this, showImage)
        slideShow.startMovie.observe(this, startMovie)
        slideShow.prepareMovie.observe(this, prepareMovie)

        // スライドショー開始
        val albumId = intent.getStringExtra(EXTRA_ALBUM_ID)
        if (albumId != null) {
            slideShow.prepare(albumId)
            slideShow.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    override fun onStop() {
        super.onStop()
        player.stop()
        slideShow.stop()
    }

    override fun onRestart() {
        super.onRestart()
        hideSystemUI()
        slideShow.resume()
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

    private val showImage = Observer<Pair<Bitmap, String>> { (bitmap, caption) ->
        hideSystemUI()
        binding.playerView.visibility = View.INVISIBLE
        player.stop()

        binding.dateText.text = caption

        nextImageView.apply {
            setImageBitmap(bitmap)
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
        currentImageView.setImageResource(0)

        binding.dateText.visibility = View.INVISIBLE
        binding.playerView.visibility = View.VISIBLE
        player.apply {
            // 先読みで追加した最後のアイテムを再生し、それより前のアイテムはプレイリストから除く
            val last = mediaItemCount - 1
            if (last >= 0) {
                seekToDefaultPosition(last)
                if (last > 0) {
                    removeMediaItems(0, last)
                }
                if (playbackState == Player.STATE_IDLE) {
                    prepare()
                }
                play()
            }
        }
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
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }


}
