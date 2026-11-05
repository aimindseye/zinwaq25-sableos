package org.sableos.titan2.printtogo.android

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import org.sableos.titan2.printtogo.core.Http
import org.sableos.titan2.printtogo.core.HttpRequest
import org.sableos.titan2.printtogo.core.HttpStatus
import org.sableos.titan2.printtogo.core.Ipp
import org.sableos.titan2.printtogo.core.IppPrinter
import org.sableos.titan2.printtogo.core.PrintJob
import org.sableos.titan2.printtogo.core.PrintToGo

/**
 * The IPP server, bound to the phone's Wi-Fi address only, so nothing reaches it over mobile data. Each
 * connection is served on its own thread, a few at a time; requests to the one printer take turns.
 */
class PrintServer(
    private val address: InetAddress,
    private val printer: IppPrinter,
    private val receive: (PrintJob, String, java.io.InputStream) -> String?
) {
    private var socket: ServerSocket? = null
    private val clients = Executors.newFixedThreadPool(MAX_CLIENTS)

    /** Starts listening; throws when the port cannot be opened. */
    fun start() {
        val server = ServerSocket(PrintToGo.PORT, BACKLOG, address)
        socket = server
        Thread({ accept(server) }, "print-to-go").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { socket?.close() }
        clients.shutdownNow()
    }

    private fun accept(server: ServerSocket) {
        while (!server.isClosed) {
            val client =
                try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
            client.soTimeout = READ_TIMEOUT_MS
            runCatching {
                clients.execute { serve(client) }
            }.onFailure { runCatching { client.close() } }
        }
    }

    private fun serve(client: Socket) {
        runCatching {
            client.use {
                val input = BufferedInputStream(it.getInputStream())
                val output = BufferedOutputStream(it.getOutputStream())
                var open = true
                while (open) {
                    val request = Http.read(input)
                    request?.let { r -> answer(r, output) }
                    open = request?.keepAlive == true
                }
            }
        }
    }

    private fun answer(request: HttpRequest, output: BufferedOutputStream) {
        val path = request.path.substringBefore('?')
        when {
            request.method == "POST" && (path == PrintToGo.PATH || path == "/") -> ipp(
                request,
                output
            )

            request.method == "GET" || request.method == "HEAD" -> {
                Http.drain(request.body)
                Http.respond(output, HttpStatus.OK, "text/plain", PAGE, request.keepAlive)
            }

            else -> {
                Http.drain(request.body)
                Http.respond(
                    output,
                    HttpStatus.NOT_ALLOWED,
                    "text/plain",
                    ByteArray(0),
                    request.keepAlive
                )
            }
        }
    }

    private fun ipp(request: HttpRequest, output: BufferedOutputStream) {
        if (request.header("content-type")?.startsWith("application/ipp") != true) {
            Http.drain(request.body)
            Http.respond(
                output,
                HttpStatus.BAD_REQUEST,
                "text/plain",
                ByteArray(0),
                request.keepAlive
            )
            return
        }
        if (request.expectsContinue) Http.writeContinue(output)
        val message = Ipp.decode(request.body)
        val response =
            synchronized(printer) {
                printer.handle(message) { job, format -> receive(job, format, request.body) }
            }
        Http.drain(request.body)
        Http.respond(
            output,
            HttpStatus.OK,
            "application/ipp",
            Ipp.encode(response),
            request.keepAlive
        )
    }

    private companion object {
        const val MAX_CLIENTS = 4
        const val BACKLOG = 8
        const val READ_TIMEOUT_MS = 60_000
        val PAGE = "Sable Print To Go\n".toByteArray(Charsets.UTF_8)
    }
}
