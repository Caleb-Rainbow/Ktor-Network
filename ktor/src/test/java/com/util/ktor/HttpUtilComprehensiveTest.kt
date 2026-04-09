package com.util.ktor

import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.model.CustomResultCode
import com.util.ktor.model.ResultCodeType
import com.util.ktor.model.ResultModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpUtilComprehensiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockConfig: NetworkConfigProvider
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setUp() {
        mockConfig = object : NetworkConfigProvider {
            override val serverAddress: String = "http://192.168.0.8"
            override val serverPort: String = "8085"
            override var token: String = ""
            override val loginPath: String = "/api/login"
            override val uploadFilePath: String = "/uploadMinio"
            override val checkUpdatePath: String = "/api/version"
            override val heartBeatPath: String = "/heart"
            override val bucketName: String = "morningcheck"
            override val username: String = "test_user"
            override val password: String = "test_password"
            override val tenant: String = ""
            override val getLogoPath: String = "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"
            override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
            override fun onNewTokenReceived(newToken: String, newTenant: String?) {}
        }
    }

    private fun createHttpUtil(engine: MockEngine): HttpUtil {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return HttpUtil(httpClient = client, json = json, config = mockConfig)
    }

    //region HttpUtil construction

    @Test
    fun `HttpUtil is constructed with valid dependencies`() {
        val engine = MockEngine { respondOk() }
        val httpUtil = createHttpUtil(engine)
        assertNotNull(httpUtil)
        assertNotNull(httpUtil.httpClient)
        assertNotNull(httpUtil.json)
        assertNotNull(httpUtil.config)
    }

    //endregion

    //region downloadFile path validation (pure logic, no Log dependency for validation)

    @Test
    fun `downloadFile rejects path with double-dot traversal`() = runTest {
        val engine = MockEngine { respondOk() }
        val httpUtil = createHttpUtil(engine)
        val result = httpUtil.downloadFile("/files/download", "/storage/../etc/passwd") { _, _, _ -> }
        assertTrue(result.isError())
        assertEquals("非法文件路径", result.message)
    }

    @Test
    fun `downloadFile rejects path with double-dot in middle`() = runTest {
        val engine = MockEngine { respondOk() }
        val httpUtil = createHttpUtil(engine)
        val result = httpUtil.downloadFile("/files/download", "/data/app/../../etc/hosts") { _, _, _ -> }
        assertTrue(result.isError())
        assertEquals("非法文件路径", result.message)
    }

    @Test
    fun `downloadFile rejects path with double-dot at start`() = runTest {
        val engine = MockEngine { respondOk() }
        val httpUtil = createHttpUtil(engine)
        val result = httpUtil.downloadFile("/files/download", "../secret.txt") { _, _, _ -> }
        assertTrue(result.isError())
        assertEquals("非法文件路径", result.message)
    }

    //endregion

    //region CustomResultCode verification

    @Test
    fun `CustomResultCode values are distinct`() {
        val codes = listOf(
            CustomResultCode.UNKNOWN_ERROR,
            CustomResultCode.TIMEOUT_ERROR,
            CustomResultCode.CONNECTION_ERROR,
            CustomResultCode.SERIALIZATION_ERROR
        )
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `CustomResultCode UNKNOWN_ERROR is 900`() {
        assertEquals(900, CustomResultCode.UNKNOWN_ERROR)
    }

    @Test
    fun `CustomResultCode TIMEOUT_ERROR is 901`() {
        assertEquals(901, CustomResultCode.TIMEOUT_ERROR)
    }

    @Test
    fun `CustomResultCode CONNECTION_ERROR is 902`() {
        assertEquals(902, CustomResultCode.CONNECTION_ERROR)
    }

    @Test
    fun `CustomResultCode SERIALIZATION_ERROR is 903`() {
        assertEquals(903, CustomResultCode.SERIALIZATION_ERROR)
    }

    //endregion

    //region ResultModel factory methods

    @Test
    fun `ResultModel success factory`() {
        val model = ResultModel.success("data")
        assertEquals(200, model.code)
        assertTrue(model.isSuccess())
    }

    @Test
    fun `ResultModel error factory`() {
        val model: ResultModel<String> = ResultModel.error("error msg")
        assertEquals(ResultCodeType.ERROR.code, model.code)
        assertTrue(model.isError())
    }

    @Test
    fun `ResultModel error with default message`() {
        val model: ResultModel<String> = ResultModel.error()
        assertEquals("未知错误", model.message)
    }

    //endregion

    //region MIME type mapping verification

    @Test
    fun `verify all expected MIME type extensions are covered`() {
        val expectedExtensions = listOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg",
            "pdf", "doc", "docx", "xls", "xlsx",
            "mp4", "avi", "mp3", "wav",
            "txt", "json", "zip", "apk",
            "pptx", "ppt", "rar", "7z",
            "wps", "et", "dps"
        )
        assertTrue(expectedExtensions.isNotEmpty())
    }

    @Test
    fun `contentType returns correct MIME for known extensions`() {
        assertEquals("image/png", File("test.png").contentType())
        assertEquals("image/jpeg", File("test.jpg").contentType())
        assertEquals("image/jpeg", File("test.jpeg").contentType())
        assertEquals("application/pdf", File("report.pdf").contentType())
        assertEquals("video/mp4", File("video.mp4").contentType())
        assertEquals("application/zip", File("archive.zip").contentType())
        assertEquals("application/vnd.android.package-archive", File("app.apk").contentType())
    }

    @Test
    fun `contentType returns octet-stream for unknown extensions`() {
        assertEquals("application/octet-stream", File("data.xyz").contentType())
        assertEquals("application/octet-stream", File("file.unknown123").contentType())
    }

    @Test
    fun `contentType is case insensitive`() {
        assertEquals("image/png", File("test.PNG").contentType())
        assertEquals("application/pdf", File("test.PDF").contentType())
    }

    @Test
    fun `contentType for file with no extension returns octet-stream`() {
        assertEquals("application/octet-stream", File("noextension").contentType())
    }

    //endregion
}
