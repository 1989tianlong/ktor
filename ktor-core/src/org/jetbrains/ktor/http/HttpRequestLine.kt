package org.jetbrains.ktor.http

@Deprecated("Use ApplicationRequest.local or ApplicationRequest.origin instead")
data class HttpRequestLine(val method: HttpMethod, val uri: String, val version: String)
