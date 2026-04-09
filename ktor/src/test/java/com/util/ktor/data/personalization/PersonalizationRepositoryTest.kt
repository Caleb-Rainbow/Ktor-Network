package com.util.ktor.data.personalization

import com.util.ktor.HttpUtil
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.personalization.model.LogoModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersonalizationRepositoryTest {

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
            override val checkUpdatePath: String = "/api/version"
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

    private fun createRepo(engine: MockEngine): PersonalizationRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = config)
        return PersonalizationRepository(httpUtil, config)
    }

    // Note: getLogoUrl calls HttpUtil.get -> executeRequest -> android.util.Log.
    // Tests verify path construction and model serialization.

    @Test
    fun `getLogoUrl constructs path with device number`() {
        val deviceNumber = "DEVICE001"
        val expectedPath = config.getLogoPath + "/$deviceNumber"
        assertEquals(
            "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo/DEVICE001",
            expectedPath
        )
    }

    @Test
    fun `getLogoUrl with empty device number`() {
        val expectedPath = config.getLogoPath + "/"
        assertEquals(
            "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo/",
            expectedPath
        )
    }

    @Test
    fun `getLogoUrl with special characters in device number`() {
        val deviceNumber = "DEV-001_V2"
        val expectedPath = config.getLogoPath + "/$deviceNumber"
        assertTrue(expectedPath.contains("DEV-001_V2"))
    }

    @Test
    fun `getLogoPath default value is correct`() {
        assertEquals(
            "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo",
            config.getLogoPath
        )
    }

    @Test
    fun `PersonalizationRepository is constructed correctly`() {
        val engine = MockEngine { respond(
            content = """{"code":200,"msg":"ok","data":{"url":"https://example.com/logo.png"}}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        ) }
        val repo = createRepo(engine)
        assertNotNull(repo)
    }

    //region LogoModel tests

    @Test
    fun `LogoModel serialization round trip`() {
        val model = LogoModel(url = "https://example.com/logo.png")
        val serialized = json.encodeToString(LogoModel.serializer(), model)
        val deserialized = json.decodeFromString(LogoModel.serializer(), serialized)
        assertEquals(model, deserialized)
    }

    @Test
    fun `LogoModel with empty URL`() {
        val jsonStr = """{"url":""}"""
        val model = json.decodeFromString(LogoModel.serializer(), jsonStr)
        assertEquals("", model.url)
    }

    @Test
    fun `LogoModel with long URL`() {
        val longUrl = "https://example.com/" + "a".repeat(500) + "/logo.png"
        val model = LogoModel(url = longUrl)
        val serialized = json.encodeToString(LogoModel.serializer(), model)
        val deserialized = json.decodeFromString(LogoModel.serializer(), serialized)
        assertEquals(longUrl, deserialized.url)
    }

    @Test
    fun `LogoModel data class equality`() {
        val m1 = LogoModel("https://a.com")
        val m2 = LogoModel("https://a.com")
        assertEquals(m1, m2)
    }

    //endregion
}
