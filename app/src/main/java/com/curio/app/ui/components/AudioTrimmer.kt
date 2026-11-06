package com.curio.app.ui.components

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * Trims an AAC/M4A audio file by decoding to PCM, selecting only the
 * [startMs]–[endMs] range, then re-encoding to AAC.
 *
 * Usage:
 *   val trimmedPath = AudioTrimmer.trim(
 *       outputDir = context.filesDir,
 *       inputPath = audioFilePath,
 *       startMs = 3000L,
 *       endMs = 12000L
 *   )
 *
 * Returns the output file path on success, or null on failure.
 */
object AudioTrimmer {

    private const val OUTPUT_MIME = "audio/mp4a-latm"
    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 1
    private const val BIT_RATE = 96000

    /**
     * Trim [inputPath] to the range [startMs, endMs].
     *
     * @param outputDir Directory for the trimmed output file.
     * @param inputPath Absolute path to the source audio.
     * @param startMs   Start of the trim region (ms).
     * @param endMs     End of the trim region (ms), <= duration.
     * @return Absolute path of the trimmed file, or null on failure.
     */
    fun trim(
        outputDir: File,
        inputPath: String,
        startMs: Long,
        endMs: Long
    ): String? {
        val inputFile = File(inputPath)
        if (!inputFile.exists() || inputFile.length() == 0L) return null
        if (endMs <= startMs) return null

        val outputFile = File(outputDir, "curio_trimmed_${System.currentTimeMillis()}.m4a")

        val extractor = try {
            MediaExtractor().apply { setDataSource(inputPath) }
        } catch (_: Exception) { return null }

        val trackIndex: Int
        val inputFormat: MediaFormat
        try {
            trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: run { extractor.release(); return null }
            inputFormat = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
        } catch (_: Exception) { extractor.release(); return null }

        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: run {
            extractor.release(); return null
        }

        // ── Decoder ─────────────────────────────────────────────────────────
        val decoder = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
        } catch (_: Exception) { extractor.release(); return null }

        // ── Encoder ─────────────────────────────────────────────────────────
        val outFormat = MediaFormat.createAudioFormat(
            OUTPUT_MIME, SAMPLE_RATE, CHANNELS
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }

        val encoder = try {
            MediaCodec.createEncoderByType(OUTPUT_MIME).apply {
                configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } catch (_: Exception) {
            decoder.stop(); decoder.release(); extractor.release(); return null
        }

        // ── Muxer ───────────────────────────────────────────────────────────
        val muxer = try {
            MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (_: Exception) {
            encoder.stop(); encoder.release()
            decoder.stop(); decoder.release(); extractor.release(); return null
        }

        var muxerTrack = -1
        var muxerStarted = false
        val bufInfo = MediaCodec.BufferInfo()
        val encBufInfo = MediaCodec.BufferInfo()

        // Decoded PCM samples accumulator (16-bit short values)
        val pcmAccumulator = mutableListOf<Short>()
        val startSample = (startMs * SAMPLE_RATE / 1000).coerceAtLeast(0)
        val endSample = (endMs * SAMPLE_RATE / 1000).coerceAtLeast(startSample + 1)
        var globalSampleIndex = 0L

        // ── Phase 1: Decode all audio, selecting only the trim range ────────
        var inputDone = false
        var decoderDone = false

        while (!decoderDone) {
            // Feed input
            if (!inputDone) {
                val idx = decoder.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    val buf = decoder.getInputBuffer(idx)!!
                    val sz = extractor.readSampleData(buf, 0)
                    if (sz < 0) {
                        decoder.queueInputBuffer(idx, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(idx, 0, sz, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Drain output
            val outIdx = decoder.dequeueOutputBuffer(bufInfo, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {/* ignore */}
                outIdx >= 0 -> {
                    val buf = decoder.getOutputBuffer(outIdx)
                    if (buf != null && bufInfo.size > 0) {
                        val frameSamples = bufInfo.size / 2
                        buf.position(bufInfo.offset)
                        buf.limit(bufInfo.offset + bufInfo.size)
                        val shortBuf = buf.asShortBuffer()
                        val frameStart = globalSampleIndex
                        for (i in 0 until frameSamples) {
                            val si = frameStart + i
                            if (si in startSample until endSample) {
                                pcmAccumulator.add(shortBuf.get())
                            } else {
                                shortBuf.get() // advance
                            }
                        }
                        globalSampleIndex += frameSamples
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderDone = true
                    }
                }
            }
        }
        decoder.stop()
        decoder.release()

        // ── Phase 2: Encode selected PCM samples to AAC and mux ────────────
        var pcmPos = 0
        val totalPcm = pcmAccumulator.size
        var encoderDone = false
        val chunkSize = 8192

        while (!encoderDone) {
            // Feed encoder
            val idx = encoder.dequeueInputBuffer(10_000)
            if (idx >= 0) {
                val buf = encoder.getInputBuffer(idx)!!
                if (pcmPos >= totalPcm) {
                    encoder.queueInputBuffer(idx, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    val chunk = minOf(chunkSize, totalPcm - pcmPos)
                    buf.rewind()
                    for (i in 0 until chunk) {
                        buf.putShort(pcmAccumulator[pcmPos + i])
                    }
                    val pts = pcmPos * 1_000_000L / SAMPLE_RATE
                    encoder.queueInputBuffer(idx, 0, chunk * 2, pts, 0)
                    pcmPos += chunk
                }
            }

            // Drain encoded output
            val encIdx = encoder.dequeueOutputBuffer(encBufInfo, 10_000)
            when {
                encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                encIdx >= 0 -> {
                    val buf = encoder.getOutputBuffer(encIdx)
                    if (buf != null && encBufInfo.size > 0 && muxerTrack >= 0) {
                        muxer.writeSampleData(muxerTrack, buf, encBufInfo)
                    }
                    encoder.releaseOutputBuffer(encIdx, false)
                    if (encBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encoderDone = true
                    }
                }
            }
        }

        encoder.stop()
        encoder.release()
        extractor.release()

        try {
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        } catch (_: Exception) {
            outputFile.delete()
            return null
        }

        return if (outputFile.exists() && outputFile.length() > 0) {
            outputFile.absolutePath
        } else {
            outputFile.delete()
            null
        }
    }
}
