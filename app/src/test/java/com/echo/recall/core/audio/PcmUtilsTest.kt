package com.echo.recall.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmUtilsTest {

    @Test
    fun `short to float and back round-trips`() {
        val src = shortArrayOf(0, 1000, -1000, 32767, -32768)
        val floats = PcmUtils.shortsToFloats(src)
        val back = PcmUtils.floatsToShorts(floats)
        assertArrayEquals(src, back)
    }

    @Test
    fun `floats are normalised into minus one to one`() {
        val floats = PcmUtils.shortsToFloats(shortArrayOf(32767, -32768, 0))
        assertEquals(1.0f, floats[0], 0.001f)
        assertEquals(-1.0f, floats[1], 0.001f)
        assertEquals(0.0f, floats[2], 0.0f)
    }

    @Test
    fun `bytes are little endian`() {
        val bytes = PcmUtils.shortsToBytes(shortArrayOf(0x0102))
        assertEquals(0x02.toByte(), bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
        assertArrayEquals(shortArrayOf(0x0102), PcmUtils.bytesToShorts(bytes))
    }

    @Test
    fun `concat joins chunks in order`() {
        val joined = PcmUtils.concat(listOf(shortArrayOf(1, 2), shortArrayOf(3), shortArrayOf(4, 5)))
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5), joined)
    }

    @Test
    fun `sample and millisecond helpers agree`() {
        assertEquals(16000, AudioSpec.msToSamples(1000))
        assertEquals(1000L, AudioSpec.samplesToMs(16000))
        assertEquals(512, AudioSpec.FRAME_SAMPLES)
    }
}
