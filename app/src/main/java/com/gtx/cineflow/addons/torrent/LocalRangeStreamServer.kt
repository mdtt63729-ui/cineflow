package com.gtx.cineflow.addons.torrent

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal loopback-only HTTP/1.1 byte-range server for Media3.
 * It binds only to 127.0.0.1, supports GET/HEAD and a single Range request,
 * bounds headers and each backend read, and never exposes the server on LAN interfaces.
 */
class LocalRangeStreamServer(
    private val sourceProvider: (String) -> TorrentContentSource?,
    private val maxConcurrentClients: Int = 4,
    private val chunkBytes: Int = 256 * 1024,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newFixedThreadPool(maxConcurrentClients.coerceIn(1, 8))
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var serverSocket: ServerSocket? = null
    val port: Int get() = serverSocket?.localPort ?: -1

    @Synchronized
    @Throws(IOException::class)
    fun start(): Int {
        if (running.get()) return port
        val socket = ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        running.set(true)
        Thread({
            while (running.get()) {
                try {
                    val client = socket.accept()
                    sockets.add(client)
                    executor.execute {
                        try { handle(client) } finally {
                            sockets.remove(client)
                            runCatching { client.close() }
                        }
                    }
                } catch (e: SocketException) {
                    if (running.get()) { /* transient accept failure; loop while active */ }
                } catch (e: IOException) {
                    if (!running.get()) break
                }
            }
        }, "cineflow-range-accept").apply { isDaemon = true }.start()
        return socket.localPort
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        try {
            val requestLine = readAsciiLine(input, 4096) ?: return
            val requestParts = requestLine.split(' ')
            if (requestParts.size != 3 || requestParts[2] !in setOf("HTTP/1.0", "HTTP/1.1")) {
                writeError(output, 400, "Bad Request"); return
            }
            val method = requestParts[0]
            if (method !in setOf("GET", "HEAD")) {
                writeError(output, 405, "Method Not Allowed", "Allow: GET, HEAD\r\n"); return
            }
            val target = requestParts[1]
            if (!target.startsWith("/stream/") || target.contains("..") || target.contains('\\')) {
                writeError(output, 404, "Not Found"); return
            }
            val id = target.removePrefix("/stream/").substringBefore('?')
            if (id.isBlank() || id.length > 128 || id.any { !(it.isLetterOrDigit() || it == '-' || it == '_') }) {
                writeError(output, 400, "Bad Request"); return
            }

            var rangeHeader: String? = null
            var totalHeaderBytes = requestLine.length
            var lineCount = 0
            while (true) {
                val line = readAsciiLine(input, 8192) ?: run { writeError(output, 400, "Bad Request"); return }
                totalHeaderBytes += line.length
                lineCount++
                if (totalHeaderBytes > 16 * 1024 || lineCount > 64) {
                    writeError(output, 431, "Request Header Fields Too Large"); return
                }
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                if (name.equals("Range", ignoreCase = true)) rangeHeader = value
            }

            val source = sourceProvider(id)
            if (source == null) { writeError(output, 404, "Stream Not Found"); return }
            val length = source.contentLength
            if (length < 0) { writeError(output, 500, "Invalid Stream Length"); return }
            val range = parseRange(rangeHeader, length)
            if (range == null) {
                if (rangeHeader != null) {
                    output.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$length\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
                    output.flush()
                    return
                }
                val start = 0L
                val end = (length - 1).coerceAtLeast(-1)
                writeHeaders(output, 200, "OK", source, start, end, length, partial = false)
                if (method == "GET" && length > 0) streamBytes(output, source, start, length)
            } else {
                val (start, end) = range
                val count = end - start + 1
                writeHeaders(output, 206, "Partial Content", source, start, end, count, partial = true)
                if (method == "GET" && count > 0) streamBytes(output, source, start, count)
            }
            output.flush()
        } catch (e: Exception) {
            // Client disconnects and backend timeouts are isolated to this connection.
            runCatching { writeError(output, 502, "Stream Temporarily Unavailable") }
        }
    }

    private fun streamBytes(output: BufferedOutputStream, source: TorrentContentSource, start: Long, count: Long) {
        var offset = start
        var remaining = count
        while (remaining > 0) {
            val size = minOf(chunkBytes.toLong(), remaining).toInt()
            val bytes = source.readRange(offset, size)
            if (bytes.isEmpty()) throw EOFException("Torrent backend returned no data before requested range completed")
            if (bytes.size > size) throw IOException("Torrent backend returned more bytes than requested")
            output.write(bytes)
            offset += bytes.size
            remaining -= bytes.size
            if (bytes.size < size && remaining > 0) throw EOFException("Torrent backend returned a truncated range")
        }
    }

    private fun writeHeaders(
        out: BufferedOutputStream, code: Int, reason: String, source: TorrentContentSource,
        start: Long, end: Long, bodyLength: Long, partial: Boolean
    ) {
        val mime = source.displayName.substringAfterLast('.', "").lowercase().let {
            when (it) {
                "m3u8" -> "application/vnd.apple.mpegurl"
                "mpd" -> "application/dash+xml"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                else -> "video/mp4"
            }
        }
        val builder = StringBuilder("HTTP/1.1 $code $reason\r\n")
        builder.append("Content-Type: $mime\r\n")
        builder.append("Accept-Ranges: bytes\r\n")
        builder.append("Content-Length: $bodyLength\r\n")
        if (partial) builder.append("Content-Range: bytes $start-$end/${source.contentLength}\r\n")
        builder.append("Connection: close\r\n\r\n")
        out.write(builder.toString().toByteArray(StandardCharsets.US_ASCII))
    }

    private fun parseRange(header: String?, length: Long): Pair<Long, Long>? {
        if (header == null) return null
        if (!header.startsWith("bytes=") || header.contains(',')) return null
        val spec = header.removePrefix("bytes=").trim()
        val dash = spec.indexOf('-')
        if (dash < 0 || length <= 0) return null
        val left = spec.substring(0, dash).trim()
        val right = spec.substring(dash + 1).trim()
        return try {
            val start: Long
            val end: Long
            if (left.isEmpty()) {
                val suffix = right.toLong()
                if (suffix <= 0) return null
                start = (length - suffix).coerceAtLeast(0)
                end = length - 1
            } else {
                start = left.toLong()
                end = if (right.isEmpty()) length - 1 else right.toLong()
            }
            if (start < 0 || start >= length || end < start) null else start to minOf(end, length - 1)
        } catch (_: NumberFormatException) { null }
    }

    private fun readAsciiLine(input: BufferedInputStream, limit: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() <= limit) {
            val b = input.read()
            if (b < 0) return if (bytes.size() == 0) null else bytes.toString("US-ASCII")
            if (b == 10) break
            if (b != 13) bytes.write(b)
        }
        if (bytes.size() > limit) throw IOException("HTTP line too long")
        return bytes.toString("US-ASCII")
    }

    private fun writeError(out: BufferedOutputStream, code: Int, reason: String, extraHeaders: String = "") {
        val body = "$code $reason".toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 $code $reason\\r\\n" +
            extraHeaders +
            "Content-Type: text/plain; charset=utf-8\\r\\n" +
            "Content-Length: ${body.size}\\r\\n" +
            "Connection: close\\r\\n\\r\\n"
        out.write(headers.toByteArray(StandardCharsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        executor.shutdownNow()
        serverSocket = null
    }
}
