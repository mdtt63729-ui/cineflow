package com.gtx.cineflow.addons.torrent

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Coordinates HTTP playback reads with the native torrent backend.
 * The backend reports pieces as ready or failed. A timeout throws and keeps the caller
 * in an explicit error/buffering path; it never pretends missing data is an empty piece.
 */
class PieceScheduler {
    private val pieces = ConcurrentHashMap<Int, CompletableFuture<Unit>>()

    fun awaitPiece(pieceIndex: Int, timeoutMillis: Long = 60_000) {
        require(pieceIndex >= 0)
        val future = pieces.computeIfAbsent(pieceIndex) { CompletableFuture() }
        try {
            future.get(timeoutMillis.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw IOException("Timed out waiting for torrent piece $pieceIndex", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting for torrent piece $pieceIndex", e)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw IOException("Torrent piece $pieceIndex failed", e.cause ?: e)
        }
    }

    fun markReady(pieceIndex: Int) {
        require(pieceIndex >= 0)
        pieces.computeIfAbsent(pieceIndex) { CompletableFuture() }.complete(Unit)
    }

    fun markFailed(pieceIndex: Int, cause: Throwable) {
        require(pieceIndex >= 0)
        pieces.computeIfAbsent(pieceIndex) { CompletableFuture() }.completeExceptionally(cause)
    }

    fun reset(pieceIndex: Int) {
        pieces.remove(pieceIndex)
    }
}
