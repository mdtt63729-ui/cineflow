package com.gtx.cineflow.addons.torrent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PieceSchedulerTest {
    @Test fun awaitCompletesWhenBackendMarksPieceReady() {
        val scheduler = PieceScheduler()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit { scheduler.awaitPiece(2, 2_000) }
            Thread.sleep(50)
            scheduler.markReady(2)
            future.get(1, TimeUnit.SECONDS)
            assertTrue(true)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(expected = IOException::class)
    fun timeoutThrowsInsteadOfReturningEmptyData() {
        PieceScheduler().awaitPiece(4, 10)
    }
}
