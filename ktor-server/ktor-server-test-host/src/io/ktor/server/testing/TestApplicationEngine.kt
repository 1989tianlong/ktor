package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

class TestApplicationEngine(
        environment: ApplicationEngineEnvironment = createTestEnvironment(),
        configure: Configuration.() -> Unit = {}
) : BaseApplicationEngine(environment, EnginePipeline()) {

    class Configuration : BaseApplicationEngine.Configuration() {
        var dispatcher: CoroutineContext = EmptyCoroutineContext
    }

    private val configuration = Configuration().apply(configure)

    init {
        pipeline.intercept(EnginePipeline.Call) {
            call.application.execute(call)
        }
    }

    override fun start(wait: Boolean): ApplicationEngine {
        environment.start()
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        environment.monitor.raise(ApplicationStopPreparing, environment)
        environment.stop()
    }

    fun handleRequest(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall(setup)
        val copyJob = launch(ioCoroutineDispatcher) {
            call.response.flush()
        }

        runBlocking(configuration.dispatcher) {
            try {
                pipeline.execute(call)
                copyJob.join()
            } catch (cause: Throwable) {
                copyJob.cancel()
                throw cause
            }
        }

        return call
    }

    fun handleWebSocket(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall {
            this.uri = uri
            addHeader(HttpHeaders.Connection, "Upgrade")
            addHeader(HttpHeaders.Upgrade, "websocket")
            addHeader(HttpHeaders.SecWebSocketKey, encodeBase64("test".toByteArray()))

            setup()
        }

        runBlocking(configuration.dispatcher) {
            pipeline.execute(call)
        }

        return call
    }

    fun handleWebSocketConversation(
            uri: String, setup: TestApplicationRequest.() -> Unit = {},
            callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
    ): TestApplicationCall {
        val websocketChannel = ByteChannel(true)
        val call = handleWebSocket(uri) {
            setup()
            body = websocketChannel
        }

        val pool = KtorDefaultPool
        val engineContext = Unconfined
        val job = Job()
        val writer = @Suppress("DEPRECATION") WebSocketWriter(websocketChannel, job, engineContext, pool)
        val reader = @Suppress("DEPRECATION") WebSocketReader(
                call.response.contentChannel()!!, { Int.MAX_VALUE.toLong() }, job, engineContext, pool
        )

        runBlocking(configuration.dispatcher) {
            call.callback(reader.incoming, writer.outgoing)
            writer.flush()
            writer.close()
            job.cancelAndJoin()
        }
        return call
    }

    fun createCall(setup: TestApplicationRequest.() -> Unit): TestApplicationCall =
            TestApplicationCall(application).apply { setup(request) }
}