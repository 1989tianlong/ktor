package io.ktor.server.testing

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.time.*
import java.util.concurrent.*

class TestApplicationResponse(call: TestApplicationCall) : BaseApplicationResponse(call) {
    @Volatile
    internal var responseReader: ReaderJob? = null

    val content: String?
        get() {
            val charset = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).charset() } ?: Charsets.UTF_8
            return byteContent?.toString(charset)
        }

    var byteContent: ByteArray? = null
        private set

    override fun setStatus(statusCode: HttpStatusCode) {}

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        private val builder = HeadersBuilder()

        override fun engineAppendHeader(name: String, value: String) {
            if (call.requestHandled)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            builder.append(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = builder.names().toList()
        override fun getEngineHeaderValues(name: String): List<String> = builder.getAll(name).orEmpty()
    }

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            call.requestHandled = true
        }
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        responseReader = reader(ioCoroutineDispatcher) {
            val length = headers[HttpHeaders.ContentLength]?.let { contentLengthString ->
                val contentLength = contentLengthString.toLong()
                if (contentLength >= Int.MAX_VALUE) throw error("Content length is too big for test engine")

                contentLength.toInt()
            }

            byteContent = channel.toByteArray(sizeHint = length ?: 0)
        }

        return responseReader!!.channel
    }

    fun contentChannel(): ByteReadChannel? = byteContent?.let { ByteReadChannel(it) }

    // Websockets & upgrade
    private val webSocketCompleted: CompletableDeferred<Unit> = CompletableDeferred()

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        val job = upgrade.upgrade(call.receiveChannel(), responseChannel(), CommonPool, Unconfined)
        val registration = job.attachChild(webSocketCompleted)

        webSocketCompleted.invokeOnCompletion { registration.dispose() }
    }

    fun awaitWebSocket(duration: Duration) = runBlocking {
        withTimeout(duration.toMillis(), TimeUnit.MILLISECONDS) {
            webSocketCompleted.join()
        }
    }
}
