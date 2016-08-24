package org.jetbrains.ktor.tests.application

class ConfigTest {
    @org.junit.Test
    fun testMapApplicationConfig() {
        val mapConfig = org.jetbrains.ktor.config.MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")

        mapConfig.put("auth.values.size", "2")
        mapConfig.put("auth.values.0", "a")
        mapConfig.put("auth.values.1", "b")

        val auth = mapConfig.config("auth")
        kotlin.test.assertEquals("ktor", auth.property("salt").getString())
        val users = auth.configList("users")
        kotlin.test.assertEquals(1, users.size)
        kotlin.test.assertEquals("test", users[0].property("name").getString())

        val values = auth.property("values").getList()
        kotlin.test.assertEquals("[a, b]", values.toString())
    }
}