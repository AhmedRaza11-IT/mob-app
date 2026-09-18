package com.whatsapp.clone.audio

import kotlin.math.max

object WaveformNormalizer {
    /**
     * Normalizes a raw array of audio PCM/amplitude readings into a fixed 0-100 scale.
     */
    fun normalize(rawAmplitudes: List<Int>, maxScale: Int = 100): List<Int> {
        if (rawAmplitudes.isEmpty()) return emptyList()
        val maxVal = rawAmplitudes.maxOrNull() ?: 1
        val safeMax = if (maxVal <= 0) 1 else maxVal
        return rawAmplitudes.map { raw ->
            val normalized = (raw.toDouble() / safeMax * maxScale).toInt()
            max(0, kotlin.math.min(maxScale, normalized))
        }
    }
}
