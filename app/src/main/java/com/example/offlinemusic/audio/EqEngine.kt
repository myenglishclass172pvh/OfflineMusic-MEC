package com.example.offlinemusic.audio

import androidx.media3.common.util.UnstableApi
import com.example.offlinemusic.EqState

/** Bộ xử lý EQ dùng chung giữa PlaybackService và UI (cùng một process). */
@UnstableApi
object EqEngine {
    val processor = BiquadEqProcessor()
    fun apply(s: EqState) = processor.update(s.gains.toFloatArray(), s.custom)
}
