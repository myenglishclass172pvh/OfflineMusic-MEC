package com.example.offlinemusic.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Equalizer DSP 6 dải tần: chuỗi 6 bộ lọc biquad (RBJ cookbook) áp dụng trực tiếp lên PCM 16-bit
 * trong pipeline của ExoPlayer, không phụ thuộc Equalizer của hãng điện thoại.
 *
 *  1 Sub-bass  : Low shelf  @ 50 Hz
 *  2 Bass      : Peaking    @ 125 Hz
 *  3 Low-mid   : Peaking    @ 350 Hz
 *  4 Mid-range : Peaking    @ 1.5 kHz (Q rộng)
 *  5 Presence  : Peaking    @ 5 kHz
 *  6 Treble    : High shelf @ 7 kHz
 */
@UnstableApi
class BiquadEqProcessor : BaseAudioProcessor() {

    private enum class Type { LOW_SHELF, PEAK, HIGH_SHELF }
    private class Band(val type: Type, val freq: Double, val q: Double)

    private val bands = listOf(
        Band(Type.LOW_SHELF, 50.0, 0.707),
        Band(Type.PEAK, 125.0, 0.9),
        Band(Type.PEAK, 350.0, 0.9),
        Band(Type.PEAK, 1500.0, 0.6),
        Band(Type.PEAK, 5000.0, 1.1),
        Band(Type.HIGH_SHELF, 7000.0, 0.707),
    )

    @Volatile private var targetGains = DoubleArray(6)
    @Volatile private var targetOn = false
    @Volatile private var version = 0
    private var applied = -1

    private var sampleRate = 44100
    private var channels = 2
    private val coef = Array(6) { DoubleArray(5) }      // b0 b1 b2 a1 a2 (đã chia a0)
    private var activeBands = IntArray(0)
    private var preamp = 1.0
    private var state = Array(2) { Array(6) { DoubleArray(4) } } // [kênh][dải][x1 x2 y1 y2]

    /** Gọi từ UI/Service. [on] = false => Normal Mode: bypass hoàn toàn. */
    fun update(gains: FloatArray, on: Boolean) {
        targetGains = DoubleArray(6) { gains.getOrElse(it) { 0f }.toDouble() }
        targetOn = on
        version++
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        state = Array(channels) { Array(6) { DoubleArray(4) } }
        applied = -1
        return inputAudioFormat
    }

    override fun onFlush() {
        applied = -1
        for (ch in state) for (b in ch) b.fill(0.0)
    }

    override fun onReset() {
        applied = -1
    }

    private fun recompute() {
        val g = targetGains
        val on = targetOn
        val act = ArrayList<Int>()
        var maxBoost = 0.0
        if (on) {
            for (i in 0 until 6) {
                if (abs(g[i]) >= 0.05 && bands[i].freq < sampleRate * 0.45) {
                    coef[i] = design(bands[i], g[i])
                    act.add(i)
                    maxBoost = max(maxBoost, g[i])
                }
            }
        }
        activeBands = act.toIntArray()
        // Dải không dùng nữa -> xóa trạng thái lọc
        for (i in 0 until 6) if (i !in act) for (ch in state) ch[i].fill(0.0)
        // Preamp chống clipping khi tăng gain
        preamp = 10.0.pow(-0.5 * maxBoost / 20.0)
    }

    private fun design(b: Band, dB: Double): DoubleArray {
        val a = 10.0.pow(dB / 40.0)
        val w0 = 2.0 * PI * b.freq / sampleRate
        val cw = cos(w0)
        val sw = sin(w0)
        val alpha = sw / (2.0 * b.q)
        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (b.type) {
            Type.PEAK -> {
                b0 = 1 + alpha * a; b1 = -2 * cw; b2 = 1 - alpha * a
                a0 = 1 + alpha / a; a1 = -2 * cw; a2 = 1 - alpha / a
            }
            Type.LOW_SHELF -> {
                val t = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cw + t)
                b1 = 2 * a * ((a - 1) - (a + 1) * cw)
                b2 = a * ((a + 1) - (a - 1) * cw - t)
                a0 = (a + 1) + (a - 1) * cw + t
                a1 = -2 * ((a - 1) + (a + 1) * cw)
                a2 = (a + 1) + (a - 1) * cw - t
            }
            Type.HIGH_SHELF -> {
                val t = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cw + t)
                b1 = -2 * a * ((a - 1) + (a + 1) * cw)
                b2 = a * ((a + 1) + (a - 1) * cw - t)
                a0 = (a + 1) - (a - 1) * cw + t
                a1 = 2 * ((a - 1) - (a + 1) * cw)
                a2 = (a + 1) - (a - 1) * cw - t
            }
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val v = version
        if (v != applied) {
            recompute()
            applied = v
        }
        val act = activeBands
        if (act.isEmpty()) {
            out.put(inputBuffer) // Normal Mode / tất cả 0 dB: truyền nguyên bản
            out.flip()
            return
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        val frames = size / (2 * channels)
        val pre = preamp
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                var x = inputBuffer.short.toDouble() * pre
                val st = state[c]
                for (bi in act) {
                    val k = coef[bi]
                    val s = st[bi]
                    val y = k[0] * x + k[1] * s[0] + k[2] * s[1] - k[3] * s[2] - k[4] * s[3]
                    s[1] = s[0]; s[0] = x
                    s[3] = s[2]; s[2] = y
                    x = y
                }
                out.putShort(x.roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}
