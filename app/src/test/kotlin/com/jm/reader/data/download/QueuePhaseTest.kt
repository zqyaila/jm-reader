package com.jm.reader.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The album progress line is the only place the queue reports itself, and it has exactly one job:
 * never claim "done" while work is still outstanding, and never claim "failed" while the rest of
 * the queue is still running.
 */
class QueuePhaseTest {

    @Test
    fun `anything in flight reads as downloading`() {
        assertEquals("downloading", albumPhase(active = 2, queued = 0, failed = 0))
        assertEquals("downloading", albumPhase(active = 0, queued = 3, failed = 0))
    }

    @Test
    fun `one failure does not paint the album red while others still run`() {
        // The whole point: a chapter failed, but two more are queued behind it. Showing "failed"
        // here is what made the download cell look dead mid-job.
        assertEquals("downloading", albumPhase(active = 1, queued = 2, failed = 1))
        assertEquals("downloading", albumPhase(active = 0, queued = 1, failed = 3))
    }

    @Test
    fun `a drained queue with failures reads as failed`() {
        assertEquals("failed", albumPhase(active = 0, queued = 0, failed = 1))
        assertEquals("failed", albumPhase(active = 0, queued = 0, failed = 12))
    }

    @Test
    fun `a drained queue with no failures reads as done`() {
        assertEquals("done", albumPhase(active = 0, queued = 0, failed = 0))
    }
}
