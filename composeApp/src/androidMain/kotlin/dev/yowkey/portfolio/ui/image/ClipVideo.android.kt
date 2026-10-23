package dev.yowkey.portfolio.ui.image

import android.annotation.SuppressLint
import android.content.Context
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.yowkey.portfolio.resources.Res

/** Android plays the 60 fps `.mp4` twins too, with the platform's (hardware) decoder through Media3's ExoPlayer. */
internal actual val playsClipVideo: Boolean = true

/**
 * An ExoPlayer drawing into a `TextureView`, which Compose clips to [cornerRadius] like any other content (a
 * `SurfaceView` would ignore the clip). The view stays transparent until its first frame, so the poster Compose
 * draws underneath shows meanwhile. It takes no touches, so swipes and taps reach the Compose content around it.
 * It is muted and asks for no audio focus, so it never pauses the owner's music.
 */
@Composable
internal actual fun ClipVideo(path: String, playing: Boolean, cornerRadius: Dp, onError: () -> Unit, modifier: Modifier) {
    val currentOnError by rememberUpdatedState(onError)
    // The factory runs once per view, so a new path needs a new view (and player).
    key(path) {
        val uri = remember { Res.getUri(path) }
        AndroidView(
            factory = { context -> ClipVideoView(context, uri) { currentOnError() } },
            modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
            update = { view -> view.wanted = playing },
            onRelease = ClipVideoView::release,
        )
    }
}

@SuppressLint("ViewConstructor")
private class ClipVideoView(context: Context, uri: String, private val onError: () -> Unit) : FrameLayout(context) {

    private val texture = TextureView(context).apply { alpha = 0f }

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setVideoTextureView(texture)
        repeatMode = Player.REPEAT_MODE_ONE
        volume = 0f
        addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    texture.alpha = 1f
                }

                override fun onPlayerError(error: PlaybackException) {
                    onError()
                }
            },
        )
        setMediaItem(MediaItem.fromUri(uri))
        prepare()
    }

    /** Whether the page wants it playing; it also waits while the window is hidden (the app in the background). */
    var wanted: Boolean = false
        set(value) {
            field = value
            sync()
        }

    private var windowShown = true
    private var released = false

    init {
        addView(texture, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        windowShown = visibility == View.VISIBLE
        sync()
    }

    private fun sync() {
        if (!released) player.playWhenReady = wanted && windowShown
    }

    fun release() {
        released = true
        player.release()
    }
}
