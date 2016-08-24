package org.jetbrains.ktor.netty.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.application.*

class NettyStaticTest : HostTestSuite<NettyApplicationHost>() {
    override fun createServer(block: Routing.() -> Unit): NettyApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment {}

        return embeddedNettyServer(config, env, routing = block)
    }
}