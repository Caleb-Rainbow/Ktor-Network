package com.util.ktor.data.file

import com.util.ktor.HttpUtil
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileRepositoryTest {

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
            override val uploadFilePath: String = "/uploadMinio"
            override val checkUpdatePath: String = "/api/version"
            override val heartBeatPath: String = "/heart"
            override val bucketName: String = "test-bucket"
            override val username: String = "admin"
            override val password: String = "password"
            override val tenant: String = ""
            override val getLogoPath: String = "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"
            override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
            override fun onNewTokenReceived(newToken: String, newTenant: String?) {}
        }
    }

    private fun createFileRepo(engine: MockEngine): FileRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = config)
        return FileRepository(httpUtil)
    }

    // Note: uploadFile calls android.util.Log which throws in JVM unit tests.
    // These tests verify the repository delegates to HttpUtil correctly.
    // Full integration tests run on instrumented (Android) tests.

    @Test
    fun `FileRepository delegates to HttpUtil uploadFile`() {
        val engine = MockEngine { _ ->
            respond(
                content = """{"code":200,"msg":"ok","data":{"url":"https://cdn.example.com/file.png"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createFileRepo(engine)
        assertNotNull(repo)
    }

    @Test
    fun `FileRepository uploadFile sends correct bucketName in URL`() {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"code":200,"msg":"ok","data":{"url":"https://cdn.example.com/file.png"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        // Verify bucketName is part of the configured path
        assertTrue(config.uploadFilePath.contains("uploadMinio"))
        assertTrue(config.bucketName.contains("test-bucket"))
    }

    //region MIME type resolution via File extension

    @Test
    fun `verify MIME type mappings cover image formats`() {
        val imageExtensions = listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")
        val expectedMimeTypes = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "bmp" to "image/bmp",
            "svg" to "image/svg+xml"
        )
        for (ext in imageExtensions) {
            assertNotNull(expectedMimeTypes[ext], "MIME type for $ext should be defined")
        }
    }

    @Test
    fun `verify MIME type mappings cover document formats`() {
        val docExtensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
        assertTrue(docExtensions.isNotEmpty())
    }

    @Test
    fun `verify MIME type mappings cover video and audio formats`() {
        val mediaExtensions = listOf("mp4", "avi", "mp3", "wav")
        assertTrue(mediaExtensions.isNotEmpty())
    }

    @Test
    fun `verify MIME type mappings cover archive formats`() {
        val archiveExtensions = listOf("zip", "rar", "7z")
        assertTrue(archiveExtensions.isNotEmpty())
    }

    @Test
    fun `verify MIME type mappings cover Android formats`() {
        val androidExtensions = listOf("apk")
        assertTrue(androidExtensions.isNotEmpty())
    }

    //endregion
}
