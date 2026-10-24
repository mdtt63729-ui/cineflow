package com.gtx.cineflow.addons.torrent

import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registry/server lifecycle for Media3 localhost URLs. A concrete libtorrent adapter can
 * register a TorrentContentSource once it has selected and begun fetching a file.
 */
class TorrentStreamRegistry : Closeable {
    private val sources = ConcurrentHashMap<String, TorrentContentSource>()
    private val closed = AtomicBoolean(false)
    private val server = LocalRangeStreamServer({ id -> sources[id] })

    @Synchronized
    @Throws(IOException::class)
    fun register(source: TorrentContentSource): RegisteredTorrentStream {
        check(!closed.get()) { "Torrent stream registry is closed" }
        require(source.contentLength > 0) { "Torrent file must have a known positive length" }
        val id = UUID.randomUUID().toString().replace("-", "")
        sources[id] = source
        return try {
            val port = server.start()
            RegisteredTorrentStream(
                id = id,
                url = "http://127.0.0.1:$port/stream/$id",
                displayName = source.displayName,
                contentLength = source.contentLength,
                close = { unregister(id) }
            )
        } catch (e: Exception) {
            sources.remove(id)
            throw e
        }
    }

    private fun unregister(id: String) {
        sources.remove(id)?.let { runCatching { it.close() } }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.close()
        sources.keys.toList().forEach(::unregister)
    }
}

data class RegisteredTorrentStream(
    val id: String,
    val url: String,
    val displayName: String,
    val contentLength: Long,
    private val close: () -> Unit,
) : Closeable {
    override fun close() = close.invoke()
}
