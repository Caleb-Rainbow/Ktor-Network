package com.util.ktor

import android.util.Log
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.login.model.UserToken
import com.util.ktor.model.CustomResultCode
import com.util.ktor.model.ResultModel
import com.util.ktor.plugin.AuthRefreshLock
import com.util.ktor.plugin.CustomAuthTriggerPlugin
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import java.io.File

private val MIME_TYPES = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "bmp" to "image/bmp",
    "svg" to "image/svg+xml",
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "mp4" to "video/mp4",
    "avi" to "video/x-msvideo",
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "txt" to "text/plain",
    "json" to "application/json",
    "zip" to "application/zip",
    "apk" to "application/vnd.android.package-archive",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "ppt" to "application/vnd.ms-powerpoint",
    "rar" to "application/vnd.rar",
    "7z" to "application/x-7z-compressed",
    "wps" to "application/kswps",
    "et" to "application/kset",
    "dps" to "application/ksdps",
)

private fun File.contentType(): String {
    val ext = extension.lowercase()
    return MIME_TYPES[ext] ?: "application/octet-stream"
}

class HttpUtil(
    val httpClient: HttpClient,
    val json: Json,
    val config: NetworkConfigProvider,
) {

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified T> executeRequest(
        serializer: KSerializer<ResultModel<T>>,
        block: HttpRequestBuilder.() -> Unit,
    ): ResultModel<T> {
        try {
            val response = httpClient.request { block() }
            val result = response.bodyAsChannel().toInputStream().use { stream ->
                json.decodeFromStream(serializer, stream)
            }
            return result
        } catch (e: Exception) {
            return handleException(e)
        }
    }

    suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> = executeRequest(serializer()) {
        this.method = method
        if (path.startsWith("http")) {
            url(path)
        } else {
            url { path(path) }
        }
        parametersMap.forEach { (key, value) -> parameter(key, value) }
        body?.let { setBody(it) }
    }

    suspend inline fun <reified T> get(
        path: String,
        parametersMap: Map<String, String> = emptyMap(),
    ) = request<T>(HttpMethod.Get, path, parametersMap = parametersMap)

    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ) = request<T>(HttpMethod.Post, path, body, parametersMap)

    suspend inline fun <reified T> delete(
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ) = request<T>(HttpMethod.Delete, path, body, parametersMap)

    suspend inline fun <reified T> put(
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ) = request<T>(HttpMethod.Put, path, body, parametersMap)

    @PublishedApi
    internal fun <T> handleException(e: Exception): ResultModel<T> {
        Log.e(TAG, e.stackTraceToString())
        return when (e) {
            is HttpRequestTimeoutException, is ConnectTimeoutException -> ResultModel(
                code = CustomResultCode.TIMEOUT_ERROR,
                message = "网络请求超时"
            )

            is UnresolvedAddressException -> ResultModel(
                code = CustomResultCode.CONNECTION_ERROR,
                message = "无法连接到服务器"
            )

            is SerializationException -> ResultModel(
                code = CustomResultCode.SERIALIZATION_ERROR,
                message = "数据解析异常: ${e.message}"
            )

            is ResponseException -> ResultModel(
                code = e.response.status.value,
                message = "服务器错误: ${e.response.status.description}"
            )

            else -> ResultModel(
                code = CustomResultCode.UNKNOWN_ERROR,
                message = "未知网络异常: ${e.message}"
            )
        }
    }

    suspend fun uploadFile(
        file: File,
        timeoutMillis: Long = 300_000L,
    ): ResultModel<JsonObject> {
        return try {
            val fileSizeMB = file.length() / (1024.0 * 1024.0)
            if (fileSizeMB > 100) {
                Log.w(
                    TAG,
                    "上传文件较大: ${String.format("%.2f", fileSizeMB)}MB, 注意内存使用"
                )
            }

            val urlPath = config.uploadFilePath
            val bucketName = config.bucketName

            val response = httpClient.submitFormWithBinaryData(
                url = "$urlPath?bucketName=$bucketName",
                formData = formData {
                    append("file", file.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, file.contentType())
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=${file.name}"
                        )
                    })
                }
            ) {
                timeout {
                    requestTimeoutMillis = timeoutMillis
                }
                headers.append(HttpHeaders.Connection, "close")
            }.bodyAsText()

            Log.d(TAG, "uploadFile path: $urlPath")
            json.decodeFromString<ResultModel<JsonObject>>(response)
        } catch (e: Exception) {
            handleException(e)
        }
    }

    suspend fun downloadFile(
        path: String,
        filePath: String,
        timeoutMillis: Long = 1_200_000L,
        onProgress: (progress: Float, speed: String, remainingTime: String) -> Unit,
    ): ResultModel<String> {
        if (filePath.contains("..")) {
            return ResultModel.error("非法文件路径")
        }
        return withContext(Dispatchers.IO) {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.outputStream().use { outputStream ->
                try {
                    httpClient.prepareGet(path) {
                        timeout {
                            requestTimeoutMillis = timeoutMillis
                        }
                    }.execute { httpResponse ->
                        val totalBytes = httpResponse.contentLength() ?: 0L
                        var bytesRead = 0L
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        val channel = httpResponse.bodyAsChannel()

                        val startTime = System.currentTimeMillis()
                        var lastUpdateTime = startTime

                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer)
                            if (read == -1) break
                            outputStream.write(buffer, 0, read)
                            bytesRead += read

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime >= 500 ||
                                bytesRead == totalBytes ||
                                bytesRead == totalBytes - 1
                            ) {
                                val progress =
                                    if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                                val elapsedTime =
                                    (currentTime - startTime).toFloat() / 1000
                                val speed = if (elapsedTime > 0)
                                    (bytesRead / elapsedTime / (1024 * 1024)).format(2)
                                else
                                    "0.00"

                                val remainingTimeSeconds = if (bytesRead > 0)
                                    (elapsedTime / bytesRead) * (totalBytes - bytesRead)
                                else
                                    0f
                                val remainingTimeFormatted =
                                    formatTime(remainingTimeSeconds.toLong())

                                onProgress(
                                    progress,
                                    "$speed MB/s",
                                    remainingTimeFormatted
                                )
                                lastUpdateTime = currentTime
                            }
                        }
                    }
                    ResultModel.success(filePath)
                } catch (e: Exception) {
                    handleException(e)
                }
            }
        }
    }

    private fun Float.format(digits: Int) = "%.${digits}f".format(this)

    private fun formatTime(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0秒"
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
    }
}

fun createLoginModel(
    json: Json,
    style: LoginKeyStyle,
    username: String,
    password: String,
): String {
    return json.encodeToString(JsonObject.serializer(), buildJsonObject {
        when (style) {
            LoginKeyStyle.CAMEL_CASE_V1 -> {
                put("userName", username)
                put("passWord", password)
            }

            LoginKeyStyle.LOWER_CASE_V2 -> {
                put("username", username)
                put("password", password)
            }
        }
    })
}

fun createDefaultHttpClient(
    config: NetworkConfigProvider,
    json: Json,
    authRefreshLock: AuthRefreshLock = AuthRefreshLock(),
): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            }
        }
        installPlugins(config, json, authRefreshLock)
    }
}

fun createNoAuthDefaultHttpClient(json: Json): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            }
        }
        installCommonPlugins(json)
    }
}

private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installCommonPlugins(
    json: Json,
) {
    install(ContentNegotiation) {
        json(json)
    }
    install(Logging) {
        logger = Logger.ANDROID
        level = LogLevel.ALL
    }
}

@OptIn(InternalAPI::class)
fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installPlugins(
    config: NetworkConfigProvider,
    json: Json,
    authRefreshLock: AuthRefreshLock = AuthRefreshLock(),
) {
    installCommonPlugins(json)

    install(CustomAuthTriggerPlugin) {
        this.json = json
    }

    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMillis
        connectTimeoutMillis = config.connectTimeoutMillis
        socketTimeoutMillis = config.socketTimeoutMillis
    }

    install(Auth) {
        bearer {
            realm = "Access to protected resources"

            loadTokens {
                val token = config.token
                if (token.isNotEmpty()) {
                    Log.d(TAG, "loadTokens: 使用现有 token")
                    BearerTokens(accessToken = token, refreshToken = null)
                } else {
                    Log.d(TAG, "loadTokens: 无已保存的 token")
                    null
                }
            }

            sendWithoutRequest { request ->
                val isLoginPath = request.url.encodedPath == config.loginPath
                val hasToken = config.token.isNotEmpty()
                !isLoginPath && hasToken
            }

            refreshTokens {
                authRefreshLock.mutex.withLock {
                    val oldTokens = this.oldTokens
                    val currentSavedToken = config.token

                    val oldTokenPreview = oldTokens?.accessToken?.let {
                        if (it.length > 16) "${it.take(6)}...${it.takeLast(6)}" else it
                    } ?: "null"
                    val savedTokenPreview = if (currentSavedToken.length > 16)
                        "${currentSavedToken.take(6)}...${currentSavedToken.takeLast(6)}"
                    else
                        currentSavedToken.ifEmpty { "empty" }
                    Log.d(
                        TAG,
                        "refreshTokens - oldToken: $oldTokenPreview, savedToken: $savedTokenPreview, 相同: ${oldTokens?.accessToken == currentSavedToken}"
                    )

                    if (currentSavedToken.isNotEmpty() && oldTokens?.accessToken != currentSavedToken) {
                        Log.d(TAG, "Token 已被更新，直接使用新 Token")
                        BearerTokens(
                            accessToken = currentSavedToken,
                            refreshToken = null
                        )
                    } else {
                        Log.d(TAG, "Token 已过期，开始执行刷新操作...")
                        val loginPayload = createLoginModel(
                            json,
                            config.getLoginKeyStyle(),
                            config.username,
                            config.password
                        )

                        val response = client.post(config.loginPath) {
                            markAsRefreshTokenRequest()
                            setBody(loginPayload)
                            contentType(ContentType.Application.Json)
                        }

                        val loginResult = response.body<ResultModel<UserToken>>()
                        if (loginResult.isSuccess() && loginResult.data != null) {
                            val newToken = loginResult.data.token
                            val newTenant = loginResult.data.tenant
                            config.onNewTokenReceived(newToken, newTenant)
                            Log.d(TAG, "Token 刷新成功")
                            BearerTokens(accessToken = newToken, refreshToken = null)
                        } else {
                            Log.e(TAG, "重新登录失败")
                            null
                        }
                    }
                }
            }
        }
    }

    install(DefaultRequest) {
        val host =
            config.serverAddress.removePrefix("https://").removePrefix("http://")
        val protocol =
            if (config.serverAddress.startsWith("https")) URLProtocol.HTTPS else URLProtocol.HTTP
        val port = config.serverPort.toIntOrNull()

        url {
            this.protocol = protocol
            this.host = host
            port?.let { this.port = it }
            contentType(ContentType.Application.Json)
        }
        val tenant = config.tenant
        if (tenant.isNotEmpty()) {
            header("tenant", tenant)
        }
    }
}

private const val TAG = "HttpUtil-Ktor"
