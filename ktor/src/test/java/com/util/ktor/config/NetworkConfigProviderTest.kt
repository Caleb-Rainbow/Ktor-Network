package com.util.ktor.config

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkConfigProviderTest {

    //region Default values

    @Test
    fun `getLogoPath returns configured URL`() {
        val config = createTestConfig()
        assertEquals(
            "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo",
            config.getLogoPath
        )
    }

    @Test
    fun `default requestTimeoutMillis is 300 seconds`() {
        val config = createTestConfig()
        assertEquals(300_000L, config.requestTimeoutMillis)
    }

    @Test
    fun `default connectTimeoutMillis is 300 seconds`() {
        val config = createTestConfig()
        assertEquals(300_000L, config.connectTimeoutMillis)
    }

    @Test
    fun `default socketTimeoutMillis is 300 seconds`() {
        val config = createTestConfig()
        assertEquals(300_000L, config.socketTimeoutMillis)
    }

    @Test
    fun `default getLoginKeyStyle is CAMEL_CASE_V1`() {
        val config = createTestConfig()
        assertEquals(LoginKeyStyle.CAMEL_CASE_V1, config.getLoginKeyStyle())
    }

    //endregion

    //region Custom implementation override

    @Test
    fun `custom timeout values override defaults`() {
        val config = object : NetworkConfigProvider by createTestConfig() {
            override val requestTimeoutMillis: Long = 60_000L
            override val connectTimeoutMillis: Long = 30_000L
            override val socketTimeoutMillis: Long = 45_000L
        }
        assertEquals(60_000L, config.requestTimeoutMillis)
        assertEquals(30_000L, config.connectTimeoutMillis)
        assertEquals(45_000L, config.socketTimeoutMillis)
    }

    @Test
    fun `custom loginKeyStyle override`() {
        val config = object : NetworkConfigProvider by createTestConfig() {
            override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.LOWER_CASE_V2
        }
        assertEquals(LoginKeyStyle.LOWER_CASE_V2, config.getLoginKeyStyle())
    }

    //endregion

    //region LoginKeyStyle enum

    @Test
    fun `LoginKeyStyle has exactly two values`() {
        assertEquals(2, LoginKeyStyle.entries.size)
    }

    @Test
    fun `LoginKeyStyle values are CAMEL_CASE_V1 and LOWER_CASE_V2`() {
        assertTrue(LoginKeyStyle.entries.contains(LoginKeyStyle.CAMEL_CASE_V1))
        assertTrue(LoginKeyStyle.entries.contains(LoginKeyStyle.LOWER_CASE_V2))
    }

    //endregion

    //region Edge cases for serverAddress parsing

    @Test
    fun `serverAddress with https prefix`() {
        val config = object : NetworkConfigProvider by createTestConfig() {
            override val serverAddress: String = "https://secure.example.com"
        }
        assertTrue(config.serverAddress.startsWith("https"))
    }

    @Test
    fun `serverAddress with http prefix`() {
        val config = object : NetworkConfigProvider by createTestConfig() {
            override val serverAddress: String = "http://insecure.example.com"
        }
        assertTrue(config.serverAddress.startsWith("http"))
        assertFalse(config.serverAddress.startsWith("https"))
    }

    @Test
    fun `serverPort can be numeric string`() {
        val config = createTestConfig()
        val port = config.serverPort.toIntOrNull()
        assertNotNull(port)
    }

    @Test
    fun `serverPort can be non-numeric string`() {
        val config = object : NetworkConfigProvider by createTestConfig() {
            override val serverPort: String = "abc"
        }
        assertEquals(null, config.serverPort.toIntOrNull())
    }

    @Test
    fun `serverPort empty string`() {
        val config = object : NetworkConfigProvider by createTestConfig() {
            override val serverPort: String = ""
        }
        assertEquals(null, config.serverPort.toIntOrNull())
    }

    @Test
    fun `token can be set and retrieved`() {
        val config = createTestConfig()
        assertEquals("", config.token)
        config.token = "new-token-123"
        assertEquals("new-token-123", config.token)
    }

    @Test
    fun `tenant can be empty`() {
        val config = createTestConfig()
        assertEquals("", config.tenant)
    }

    @Test
    fun `tenant can be non-empty`() {
        val config = object : NetworkConfigProvider by createTestConfig() {
            override val tenant: String = "tenant-001"
        }
        assertEquals("tenant-001", config.tenant)
    }

    //endregion

    //region onNewTokenReceived callback

    @Test
    fun `onNewTokenReceived is called with token and tenant`() {
        var receivedToken: String? = null
        var receivedTenant: String? = null
        val config = object : NetworkConfigProvider by createTestConfig() {
            override fun onNewTokenReceived(newToken: String, newTenant: String?) {
                receivedToken = newToken
                receivedTenant = newTenant
            }
        }
        config.onNewTokenReceived("token-abc", "tenant-xyz")
        assertEquals("token-abc", receivedToken)
        assertEquals("tenant-xyz", receivedTenant)
    }

    @Test
    fun `onNewTokenReceived with null tenant`() {
        var receivedTenant: String? = "initial"
        val config = object : NetworkConfigProvider by createTestConfig() {
            override fun onNewTokenReceived(newToken: String, newTenant: String?) {
                receivedTenant = newTenant
            }
        }
        config.onNewTokenReceived("token-abc", null)
        assertEquals(null, receivedTenant)
    }

    //endregion

    private fun createTestConfig() = object : NetworkConfigProvider {
        override val serverAddress: String = "http://192.168.0.8"
        override val serverPort: String = "8085"
        override var token: String = ""
        override val loginPath: String = "/api/login"
        override val uploadFilePath: String = "/uploadMinio"
        override val checkUpdatePath: String = "/api/version"
        override val heartBeatPath: String = "/heart"
        override val bucketName: String = "morningcheck"
        override val username: String = "demo"
        override val password: String = "123456"
        override val tenant: String = ""
        override val getLogoPath: String = "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"
        override fun onNewTokenReceived(newToken: String, newTenant: String?) {}
    }
}
