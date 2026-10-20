package com.animepub.app

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import java.io.File

/**
 * Plays a downloaded episode
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var prefs: android.content.SharedPreferences
    private var path = ""

    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        path = intent.getStringExtra(EXTRA_PATH) ?: ""
        val file = File(path)
        if (!file.isFile) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.title).text = intent.getStringExtra(EXTRA_TITLE) ?: file.nameWithoutExtension
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        prefs = getSharedPreferences("player", MODE_PRIVATE)
        val resumeAt = prefs.getLong(path, 0L)

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        playerView = findViewById(R.id.playerView)
        playerView.player = player
        playerView.keepScreenOn = true
        playerView.setShowFastForwardButton(true)
        playerView.setShowRewindButton(true)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) prefs.edit().remove(path).apply()
            }
        })

        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), resumeAt)
        player.prepare()
        player.playWhenReady = true

        findViewById<ImageButton>(R.id.btnSpeed).setOnClickListener { showSpeedMenu(it) }
        findViewById<ImageButton>(R.id.btnAudio).setOnClickListener {
            TrackSelectionDialogBuilder(this, "Audio", player, C.TRACK_TYPE_AUDIO).build().show()
        }
        findViewById<ImageButton>(R.id.btnSubs).setOnClickListener {
            TrackSelectionDialogBuilder(this, "Subtitles", player, C.TRACK_TYPE_TEXT).build().show()
        }
        findViewById<ImageButton>(R.id.btnRotate).setOnClickListener {
            requestedOrientation = if (isLandscape())
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersive(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    /** Full screen picture in landscape, normal status bar in portrait. */
    private fun applyImmersive(landscape: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !landscape)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (landscape) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
        findViewById<View>(R.id.topBar).visibility = if (landscape) View.GONE else View.VISIBLE
        findViewById<View>(R.id.bottomBar).visibility = if (landscape) View.GONE else View.VISIBLE
    }

    private fun showSpeedMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        speeds.forEachIndexed { i, s ->
            menu.menu.add(0, i, i, "${s}x").apply {
                isChecked = player.playbackParameters.speed == s
            }
        }
        menu.menu.setGroupCheckable(0, true, true)
        menu.setOnMenuItemClickListener { item ->
            player.setPlaybackSpeed(speeds[item.itemId])
            true
        }
        menu.show()
    }

    /** Picture-in-picture when the user leaves while the video is playing. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26 && player.isPlaying) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPicture: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPicture, newConfig)
        findViewById<View>(R.id.topBar).visibility = if (isInPictureInPicture) View.GONE else View.VISIBLE
        findViewById<View>(R.id.bottomBar).visibility = if (isInPictureInPicture) View.GONE else View.VISIBLE
        playerView.useController = !isInPictureInPicture
    }

    override fun onPause() {
        super.onPause()
        savePosition()
        if (!isInPictureInPictureMode) player.pause()
    }

    private fun savePosition() {
        if (!::player.isInitialized || path.isEmpty()) return
        val pos = player.currentPosition
        val dur = player.duration
        // Don't resume from the last few seconds of an episode.
        if (dur > 0 && pos > dur - 5_000) prefs.edit().remove(path).apply()
        else prefs.edit().putLong(path, pos).apply()
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            savePosition()
            player.release()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
    }
}
