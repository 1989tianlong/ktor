package org.jetbrains.ktor.tests.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*

class JettyHostTest : org.jetbrains.ktor.testing.HostTestSuite<JettyApplicationHost>() {

    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, block: Routing.() -> Unit): JettyApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment(envInit)

        return embeddedJettyServer(config, env, routing = block)
    }
}