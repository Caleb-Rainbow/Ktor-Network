package com.util.ktor.data.version

import com.util.ktor.HttpUtil
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.version.model.Version
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionRepositoryTest {

    private lateinit var json: Json
    private lateinit var config: NetworkConfigProvider

    @Before
    fun setUp() {
        json = Json { ignoreUnknownKeys = true }
        config = object : NetworkConfigProvider {
            override val serverAddress: String = "http://localhost"
            override val serverPort: String = "8080"
            override var token: String = ""
            override val loginPath: String = "/api/login"
            override val uploadFilePath: String = "/upload"
            override val checkUpdatePath: String = "/api/version/check"
            override val heartBeatPath: String = "/heart"
            override val bucketName: String = "test"
            override val username: String = "admin"
            override val password: String = "password"
            override val tenant: String = ""
            override val getLogoPath: String = "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"
            override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
            override fun onNewTokenReceived(newToken: String, newTenant: String?) {}
        }
    }

    private fun createVersionRepo(engine: MockEngine): VersionRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = config)
        return VersionRepository(httpUtil, config)
    }

    //region Version model serialization tests (pure, no android.util.Log dependency)

    @Test
    fun `Version model serialization round trip`() {
        val version = Version(
            appName = "TestApp",
            code = 3,
            name = "2.0.0",
            url = "https://example.com/v2.apk",
            size = "50MB",
            description = "Major update"
        )
        val serialized = json.encodeToString(Version.serializer(), version)
        val deserialized = json.decodeFromString(Version.serializer(), serialized)
        assertEquals(version, deserialized)
    }

    @Test
    fun `Version model with null size`() {
        val jsonStr = """{
            "appName":"App","code":1,"name":"1.0",
            "url":"https://a.com","size":null,"description":"desc"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertNull(version.size)
    }

    @Test
    fun `Version model with empty strings`() {
        val version = Version(
            appName = "",
            code = 0,
            name = "",
            url = "",
            size = null,
            description = ""
        )
        val serialized = json.encodeToString(Version.serializer(), version)
        val deserialized = json.decodeFromString(Version.serializer(), serialized)
        assertEquals("", deserialized.appName)
        assertEquals(0, deserialized.code)
    }

    @Test
    fun `Version model with large version code`() {
        val jsonStr = """{
            "appName":"App","code":999999,"name":"99.99.99",
            "url":"https://a.com","size":null,"description":"desc"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals(999999, version.code)
    }

    @Test
    fun `Version model with negative version code`() {
        val jsonStr = """{
            "appName":"App","code":-1,"name":"-1.0",
            "url":"https://a.com","size":null,"description":"desc"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals(-1, version.code)
    }

    @Test
    fun `Version model with all fields populated`() {
        val jsonStr = """{
            "appName":"MyApp","code":10,"name":"3.5.1",
            "url":"https://cdn.example.com/app-3.5.1.apk",
            "size":"128.5MB",
            "description":"新功能：支持深色模式\\n修复：登录页面闪退问题"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals("MyApp", version.appName)
        assertEquals(10, version.code)
        assertEquals("3.5.1", version.name)
        assertEquals("https://cdn.example.com/app-3.5.1.apk", version.url)
        assertEquals("128.5MB", version.size)
        assertTrue(version.description!!.contains("深色模式"))
    }

    @Test
    fun `Version model with unicode in description`() {
        val jsonStr = """{
            "appName":"应用","code":1,"name":"1.0",
            "url":"https://a.com","size":null,"description":"🎉全新版本发布！"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals("🎉全新版本发布！", version.description)
    }

    @Test
    fun `Version model data class equality`() {
        val v1 = Version("App", 1, "1.0", "http://a.com", null, "desc")
        val v2 = Version("App", 1, "1.0", "http://a.com", null, "desc")
        assertEquals(v1, v2)
    }

    @Test
    fun `Version model data class copy`() {
        val v1 = Version("App", 1, "1.0", "http://a.com", null, "desc")
        val v2 = v1.copy(code = 2, name = "2.0")
        assertEquals(2, v2.code)
        assertEquals("2.0", v2.name)
        assertEquals("App", v2.appName)
        assertEquals(1, v1.code)
    }

    //endregion
}
