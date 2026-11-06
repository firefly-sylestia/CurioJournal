package com.curio.app.features.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.curio.app.data.AudioQuality
import com.curio.app.data.AudioQualitySettings
import java.io.File
import java.io.IOException

/**
 * Thin wrapper around Android's [MediaRecorder] for voice capture.
 *
 * State machine: IDLE → RECORDING → (PAUSED ↔ RECORDING) → STOPPED → IDLE
 *
 * Audio is recorded to a temporary .m4a file in the app's cache directory.
 * The file persists until [release] is called or the app process ends.
 *
 * Recording quality is read from [AudioQualitySettings] on each [start] call
 * so that changes made in ProfileScreen take effect immediately.
 *
 * Usage:
 *   val recorder = AudioRecorder(context)
 *   recorder.start()
 *   // ... pause/resume as needed ...
 *   val path: String = recorder.stop()
 *   // path is now ready for persistence
 *   recorder.release()
 */
class AudioRecorder(private val context: Context) {

    /** Current recording state. */
    enum class State { IDLE, RECORDING, PAUSED, STOPPED }

    var state: State = State.IDLE
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Elapsed recording time in seconds (approx, for UI display). */
    private var startTimeMillis: Long = 0L
    private var pausedDurationMillis: Long = 0L

    val elapsedSeconds: Int
        get() {
            if (state == State.IDLE) return 0
            val paused = if (state == State.PAUSED) {
                System.currentTimeMillis() - (startTimeMillis + pausedDurationMillis)
            } else {
                pausedDurationMillis
            }
            return ((System.currentTimeMillis() - startTimeMillis - paused) / 1000).toInt().coerceAtLeast(0)
        }

    /**
     * Real microphone input level (0..32767) while recording — drives the
     * live visualizer. Returns 0 outside RECORDING/PAUSED.
     */
    val maxAmplitude: Int
        get() = mediaRecorder?.maxAmplitude ?: 0

    /**
     * Start recording to a new temporary file.
     * Throws [IllegalStateException] if not in IDLE state.
     */
    @Throws(IOException::class, IllegalStateException::class)
    fun start() {
        check(state == State.IDLE) { "Can only start from IDLE, current: $state" }

        outputFile = File(context.cacheDir, "curio_recording_${System.currentTimeMillis()}.m4a")
            .also { it.delete() } // clean up any leftover

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        val quality = AudioQualitySettings.get(context)

        mediaRecorder!!.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(quality.samplingRate)
            setAudioEncodingBitRate(quality.bitRate)
            setOutputFile(outputFile!!.absolutePath)

            prepare()
            start()
        }

        startTimeMillis = System.currentTimeMillis()
        pausedDurationMillis = 0L
        state = State.RECORDING
    }

    /** Pause the active recording. Idempotent — no-op if not RECORDING. */
    fun pause() {
        if (state != State.RECORDING) return
        mediaRecorder?.pause()
        pausedDurationMillis = System.currentTimeMillis() - startTimeMillis
        state = State.PAUSED
    }

    /** Resume a paused recording. Idempotent — no-op if not PAUSED. */
    fun resume() {
        if (state != State.PAUSED) return
        mediaRecorder?.resume()
        // Reset pausedDuration so the elapsed formula counts only time
        // accumulated during the *previous* recording segment. After
        // resume, startTimeMillis already accounts for the pre-pause
        // recording time (it was rolled back by pausedDurationMillis),
        // so we zero out the pause accumulator to avoid double-subtracting.
        startTimeMillis = System.currentTimeMillis() - pausedDurationMillis
        pausedDurationMillis = 0L
        state = State.RECORDING
    }

    /**
     * Stop recording and return the absolute file path of the saved audio.
     * Throws [IllegalStateException] if not actively recording or paused.
     */
    fun stop(): String {
        check(state == State.RECORDING || state == State.PAUSED) {
            "Can only stop from RECORDING or PAUSED, current: $state"
        }

        try {
            mediaRecorder?.stop()
        } catch (_: RuntimeException) {
            // MediaRecorder.stop() can throw if called too quickly after start
        }
        mediaRecorder?.release()
        mediaRecorder = null
        state = State.STOPPED

        return outputFile?.absolutePath ?: error("No output file set")
    }

    /**
     * Release all resources and delete the temporary file.
     * Safe to call from any state.
     */
    fun release() {
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
        state = State.IDLE
    }

    /** Discard the current recording (deletes file, resets state). */
    fun discard() {
        try { mediaRecorder?.apply { try { stop() } catch (_: Exception) {}; release() } } catch (_: Exception) {}
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
        state = State.IDLE
    }

}
