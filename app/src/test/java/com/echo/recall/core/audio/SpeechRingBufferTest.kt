package com.echo.recall.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRingBufferTest {

    private fun seg(startMs: Long, durationMs: Long, samples: Int = 1600) =
        SpeechRingBuffer.Segment(startMs, startMs + durationMs, ShortArray(samples))

    @Test
    fun `keeps segments inside the window and drops whole older ones`() {
        val buffer = SpeechRingBuffer()
        buffer.add(seg(0, 1_000))          // ends at 1_000
        buffer.add(seg(2_000, 1_000))      // ends at 3_000
        buffer.add(seg(4_000, 1_000))      // ends at 5_000

        // window = 3s, now = 5_000 -> cutoff 2_000, segment ending at 1_000 is out
        buffer.trim(nowMs = 5_000, windowMs = 3_000)

        val kept = buffer.snapshot()
        assertEquals(2, kept.size)
        assertEquals(2_000L, kept.first().startMs)
        assertEquals(5_000L, kept.last().endMs)
    }

    @Test
    fun `does not cut a segment that straddles the window start`() {
        val buffer = SpeechRingBuffer()
        buffer.add(seg(0, 5_000))          // long utterance spanning the boundary
        buffer.add(seg(6_000, 1_000))

        buffer.trim(nowMs = 7_000, windowMs = 5_000) // cutoff = 2_000

        val kept = buffer.snapshot()
        assertEquals(2, kept.size)         // whole first segment is retained
        assertEquals(0L, kept.first().startMs)
    }

    @Test
    fun `snapshotAndClear takes everything and leaves the buffer empty`() {
        val buffer = SpeechRingBuffer()
        buffer.add(seg(0, 1_000))
        buffer.add(seg(2_000, 1_000))

        val taken = buffer.snapshotAndClear()

        assertEquals(2, taken.size)
        assertEquals(0, buffer.size)
        assertEquals(0, buffer.bytes)
        assertTrue(buffer.snapshot().isEmpty())
        assertNull(buffer.lastVoiceEndMs())
    }

    @Test
    fun `byte cap evicts oldest segments but keeps at least one`() {
        val buffer = SpeechRingBuffer(maxBytes = 4_000) // 2 segments of 1600 samples (3200 B each)
        buffer.add(seg(0, 1_000, samples = 1600))
        buffer.add(seg(2_000, 1_000, samples = 1600))
        buffer.add(seg(4_000, 1_000, samples = 1600))

        val kept = buffer.snapshot()
        assertTrue("should evict oldest", kept.size <= 2)
        assertEquals(4_000L, kept.last().startMs)
        assertTrue(buffer.bytes <= 4_000 || kept.size == 1)
    }

    @Test
    fun `reports voiced duration and last voice timestamp`() {
        val buffer = SpeechRingBuffer()
        assertNull(buffer.lastVoiceEndMs())
        assertEquals(0L, buffer.voicedMillis())

        buffer.add(seg(1_000, 1_500))
        buffer.add(seg(4_000, 500))

        assertEquals(2_000L, buffer.voicedMillis())
        assertEquals(4_500L, buffer.lastVoiceEndMs())
        assertEquals(1_000L, buffer.firstVoiceStartMs())
    }

    @Test
    fun `ignores empty segments`() {
        val buffer = SpeechRingBuffer()
        buffer.add(SpeechRingBuffer.Segment(0, 100, ShortArray(0)))
        assertEquals(0, buffer.size)
    }
}
