/*
 * Created by vfzet on 29.06.25, 23:39
 * Copyright (c) 2025 . All rights reserved.
 * Last modified 29.06.25, 23:39
 */

package com.manuelost.app.omldatatransfer.data

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.datacenter.camera.StreamInfo
import java.nio.ByteBuffer

class VideoEncoder(private val outputPath: String) {

    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var started = false

    private var vps: ByteArray? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    /** Feeds one NAL‑unit worth of bytes coming from the drone */
    fun handleFrame(
        data: ByteArray, offset: Int, length: Int,
        info: StreamInfo
    ) {
        // 1. Cache codec‑config NALs until we have them all
        if (!started) {
            cacheCodecConfig(data, offset, length, info.mimeType)
            val haveConfig =
                if (info.mimeType == ICameraStreamManager.MimeType.H264)
                    sps != null && pps != null
                else
                    vps != null && sps != null && pps != null
            if (haveConfig) initMuxer(info)
            else return                                // don’t write before muxer exists
        }

        // 2. Wrap buffer and write to MP4
        val bb = ByteBuffer.wrap(data, offset, length)
        val bufferInfo = MediaCodec.BufferInfo().apply {
            set(
                0,
                length,
                info.getPresentationTimeMs() * 1_000L,
                if (info.isKeyFrame()) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            )
        }
        muxer?.writeSampleData(trackIndex, bb, bufferInfo)
    }

    /** Start the MP4 muxer once we know resolution + codec config */
    private fun initMuxer(info: StreamInfo) {
        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val mime = when (info.getMimeType()) {
            ICameraStreamManager.MimeType.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            ICameraStreamManager.MimeType.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
        }

        val format = MediaFormat.createVideoFormat(mime, info.getWidth(), info.getHeight()).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, info.getFrameRate())
            if (info.getMimeType() == ICameraStreamManager.MimeType.H264) {
                setByteBuffer("csd-0", ByteBuffer.wrap(sps))
                setByteBuffer("csd-1", ByteBuffer.wrap(pps))
            } else {
                setByteBuffer("csd-0", ByteBuffer.wrap(vps))
                setByteBuffer("csd-1", ByteBuffer.wrap(sps))
                setByteBuffer("csd-2", ByteBuffer.wrap(pps))
            }
        }

        trackIndex = muxer!!.addTrack(format)
        muxer!!.start()
        started = true
    }


    /** Extract SPS/PPS/VPS from an Annex‑B NAL stream */
    private fun cacheCodecConfig(
        buf: ByteArray, off: Int, len: Int,
        mime: ICameraStreamManager.MimeType
    ) {
        var i = off
        val end = off + len - 4
        while (i <= end) {
            // look for 0x00 00 00 01 start code
            if (buf[i] == 0.toByte() && buf[i + 1] == 0.toByte() &&
                buf[i + 2] == 0.toByte() && buf[i + 3] == 1.toByte()
            ) {
                val nalStart = i + 4
                val nalType = if (mime == ICameraStreamManager.MimeType.H264)
                    buf[nalStart].toInt() and 0x1F
                else
                    (buf[nalStart].toInt() and 0x7E) shr 1

                val next = findNextStartCode(buf, nalStart, off + len)
                val nal = buf.copyOfRange(nalStart, next)

                when (mime) {
                    ICameraStreamManager.MimeType.H264 -> when (nalType) {
                        7 -> if (sps == null) sps = nal     // SPS
                        8 -> if (pps == null) pps = nal     // PPS
                    }
                    else -> when (nalType) {               // HEVC
                        32 -> if (vps == null) vps = nal    // VPS
                        33 -> if (sps == null) sps = nal    // SPS
                        34 -> if (pps == null) pps = nal    // PPS
                    }
                }
                i = next
            } else i++
        }
    }

    private fun findNextStartCode(b: ByteArray, from: Int, to: Int): Int {
        var p = from
        while (p <= to - 4) {
            if (b[p] == 0.toByte() && b[p + 1] == 0.toByte() &&
                b[p + 2] == 0.toByte() && b[p + 3] == 1.toByte()
            ) return p
            p++
        }
        return to
    }

    fun release() {
        try { muxer?.stop() } catch (_: Exception) {}
        muxer?.release()
        started = false
    }
}
