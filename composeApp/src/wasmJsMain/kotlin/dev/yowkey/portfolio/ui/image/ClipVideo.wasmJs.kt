package dev.yowkey.portfolio.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.HtmlElementView
import dev.yowkey.portfolio.resources.Res
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

internal actual val playsClipVideo: Boolean = true

/**
 * An HTML `<video>`, placed by Compose over the canvas at this composable's bounds and clipped to its parent's
 * (the viewer's pager) as a rectangle. The browser decodes it, in hardware where it can, so playing costs the UI
 * thread nothing per frame. It is muted and inline, which every browser lets play without a tap.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun ClipVideo(path: String, playing: Boolean, cornerRadius: Dp, onError: () -> Unit, modifier: Modifier) {
    val currentOnError by rememberUpdatedState(onError)
    // The factory runs once per element, so a new path needs a new element.
    key(path) {
        val url = remember { Res.getUri(path) }
        HtmlElementView(
            factory = { (document.createElement("video") as HTMLElement).also { setUpVideo(it, url) { currentOnError() } } },
            modifier = modifier,
            update = { video ->
                styleVideo(video, cornerRadius.value.toDouble())
                if (playing) playVideo(video) else pauseVideo(video)
            },
            onRelease = ::releaseVideo,
        )
    }
}

/**
 * The poster drawn by Compose under the element shows through until the first frame arrives. `aria-hidden`: that
 * poster already carries the caption for screen readers.
 */
private fun setUpVideo(video: HTMLElement, url: String, onError: () -> Unit): Unit = js(
    """{
        video.muted = true;
        video.defaultMuted = true;
        video.loop = true;
        video.playsInline = true;
        video.disablePictureInPicture = true;
        video.preload = 'auto';
        video.setAttribute('muted', '');
        video.setAttribute('playsinline', '');
        video.setAttribute('aria-hidden', 'true');
        video.style.display = 'block';
        video.style.objectFit = 'cover';
        video.style.pointerEvents = 'none';
        video.addEventListener('error', function () { onError(); });
        video.src = url;
    }""",
)

/**
 * Compose puts the video in a wrapper it sizes and positions; neither may take pointer events from the canvas, or
 * the viewer would stop seeing swipes and taps over the clip.
 */
private fun styleVideo(video: HTMLElement, cornerRadiusPx: Double): Unit = js(
    """{
        video.style.borderRadius = cornerRadiusPx + 'px';
        if (video.parentElement) video.parentElement.style.pointerEvents = 'none';
    }""",
)

/** A rejected play() (the tab went to the background first, say) only leaves the video paused. */
private fun playVideo(video: HTMLElement): Unit = js("{ const p = video.play(); if (p) p.catch(function () {}); }")

private fun pauseVideo(video: HTMLElement): Unit = js("video.pause()")

/** Dropping the source frees the browser's decoder now instead of when the element is collected. */
private fun releaseVideo(video: HTMLElement): Unit = js("{ video.pause(); video.removeAttribute('src'); video.load(); }")
