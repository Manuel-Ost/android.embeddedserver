/*
 * Created by vfzet on 29.06.25, 23:39
 * Copyright (c) 2025 . All rights reserved.
 * Last modified 29.06.25, 23:39
 */

package com.manuelost.app.omldatatransfer.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class VideoEncoder(private val outputPath: String) {

    private lateinit var mediaCodec: MediaCodec
    private lateinit var mediaMuxer: MediaMuxer
    private var trackIndex = -1
    private var isMuxerStarted = false

    fun initEncoder(width: Int, height: Int, frameRate: Int, bitRate: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        mediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun encodeFrame(inputBuffer: ByteBuffer, presentationTimeUs: Long) {
        val inputIndex = mediaCodec.dequeueInputBuffer(10000)
        if (inputIndex >= 0) {
            val buffer = mediaCodec.getInputBuffer(inputIndex)
            buffer?.clear()
            buffer?.put(inputBuffer)
            mediaCodec.queueInputBuffer(inputIndex, 0, inputBuffer.remaining(), presentationTimeUs, 0)
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
        while (outputIndex >= 0) {
            val encodedData = mediaCodec.getOutputBuffer(outputIndex)
            if (encodedData != null && bufferInfo.size > 0) {
                if (!isMuxerStarted) {
                    trackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
                    mediaMuxer.start()
                    isMuxerStarted = true
                }
                mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
            }
            mediaCodec.releaseOutputBuffer(outputIndex, false)
            outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
        }
    }

    fun release() {
        mediaCodec.stop()
        mediaCodec.release()
        if (isMuxerStarted) {
            mediaMuxer.stop()
            mediaMuxer.release()
        }
    }
}