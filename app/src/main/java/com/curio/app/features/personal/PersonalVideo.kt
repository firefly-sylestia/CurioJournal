package com.curio.app.features.personal

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons

/**
 * v517 — A VIDEO ON THE PAGE.
 *
 * The member's answer, when asked how a clip should behave: **inline preview,
 * tap to expand full screen**. So the block is the clip itself — a 16:9 frame
 * that shows its first picture at rest — and a tap hands it the whole window.
 *
 * It is drawn with the framework's own [VideoView] rather than a new player
 * dependency: the page does not need scrubbing or tracks yet, only "show it, let
 * me play it, let me see it big", and one AndroidView is the smaller promise.
 *
 * ⚠️ THE PERMISSION IS THE PICTURE'S: the picker already takes a PERSISTED read
 * grant for every URI it hands over, so a clip recorded today still plays next
 * week (see PersonalPage's picker).
 */
@Composable
internal fun PersonalVideoBlock(
    uri: String,
    ink: Color,
    accent: Color,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    var expanded by remember(uri) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
        ) {
            VideoFrame(
                uri = uri,
                play = false,
                modifier = Modifier.fillMaxSize()
            )
            // The still is the preview; this is the tap that makes it play big.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = enabled) { expanded = true }
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    CurioIcon(CurioIcons.ChevronRight, "Play video", tint = Color.White, size = 22.dp)
                }
            }
            if (enabled) {
                Surface(
                    onClick = onRemove,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                ) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                        CurioIcon(CurioIcons.Close, "Remove video", tint = Color.White, size = 14.dp)
                    }
                }
            }
        }
        Text(
            "Video",
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 2.dp)
        )
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoFrame(
                    uri = uri,
                    play = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { expanded = false }) {
                        CurioIcon(CurioIcons.Close, "Close video", tint = Color.White, size = 22.dp)
                    }
                }
            }
        }
    }
}

/**
 * THE FRAME. `play = false` shows the clip's first picture (a seek to 1 ms and a
 * pause, which is what puts a still on the surface); `play = true` runs it.
 * The view is stopped when the block leaves, so a page of clips is not a page of
 * decoders.
 */
@Composable
private fun VideoFrame(
    uri: String,
    play: Boolean,
    modifier: Modifier = Modifier
) {
    val view = remember(uri) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(uri) {
        onDispose { view.value?.stopPlayback() }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                setVideoURI(Uri.parse(uri))
                setOnPreparedListener { player ->
                    player.isLooping = true
                    if (play) start() else {
                        seekTo(1)
                        pause()
                    }
                }
                view.value = this
            }
        },
        update = { video ->
            if (play && !video.isPlaying) runCatching { video.start() }
        }
    )
}
