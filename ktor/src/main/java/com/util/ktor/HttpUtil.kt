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
import io.ktor.client.engine.cio.CIO
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
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import java.io.File

/**
 * @description http请求封装
 * @author 杨帅林
 * @create 2024/10/18 14:09
 **/
class HttpUtil(
    val httpClient: HttpClient,
    val json: Json,
    val config: NetworkConfigProvider,
) {
    /**
     * 通用的请求执行器，包含自动重登录逻辑
     */
    suspend fun <T> executeRequest(
        serializer: KSerializer<ResultModel<T>>,
        block: HttpRequestBuilder.() -> Unit,
    ): ResultModel<T> {
        try {
            val response = httpClient.request { block() }
            val responseText = response.bodyAsText()
            // 检查 Content-Type，防止 HTML 页面导致反序列化崩溃
            val contentType = response.headers[HttpHeaders.ContentType]
            if (contentType?.contains("application/json") != true && response.status.isSuccess()) {
                // 如果请求成功但返回的不是JSON，这是一个需要处理的异常情况
                return handleException(ResponseException(response, "Invalid Content-Type: Expected JSON but got $contentType"))
            }
            val result = json.decodeFromString(serializer, responseText)
            Log.d("HTTP_${response.request.method.value}", "path: ${response.request.url.encodedPath} response: $result")
            return result
        } catch (e: Exception) {
            return handleException(e)
        }
    }


    // GET 请求
    suspend inline fun <reified T> get(
        path: String,
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return executeRequest(serializer()) {
            method = HttpMethod.Get
            if (path.startsWith("http")) {
                url(path)
            } else {
                url {
                    path(path)
                }
            }
            parametersMap.forEach { (key, value) -> parameter(key, value) }
        }
    }

    // POST 请求
    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return executeRequest(serializer()) {
            method = HttpMethod.Post
            if (path.startsWith("http")) {
                url(path)
            } else {
                url {
                    path(path)
                }
            }
            parametersMap.forEach { (key, value) -> parameter(key, value) }
            body?.let { setBody(it) } // ContentNegotiation 会自动处理序列化
        }
    }

    // DELETE 请求
    suspend inline fun <reified T> delete(
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return executeRequest(serializer()) {
            method = HttpMethod.Delete
            if (path.startsWith("http")) {
                url(path)
            } else {
                url {
                    path(path)
                }
            }
            parametersMap.forEach { (key, value) -> parameter(key, value) }
            body?.let { setBody(it) } // ContentNegotiation 会自动处理序列化
        }
    }

    // PUT 请求
    suspend inline fun <reified T> put(
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return executeRequest(serializer()) {
            method = HttpMethod.Put
            if (path.startsWith("http")) {
                url(path)
            } else {
                url {
                    path(path)
                }
            }
            parametersMap.forEach { (key, value) -> parameter(key, value) }
            body?.let { setBody(it) } // ContentNegotiation 会自动处理序列化
        }
    }

    private fun <T> handleException(e: Exception): ResultModel<T> {
        Log.e("http", e.stackTraceToString())
        // 根据异常类型，映射到我们自定义的错误码和消息
        return when (e) {
            is HttpRequestTimeoutException, is ConnectTimeoutException -> ResultModel(
                code = CustomResultCode.TIMEOUT_ERROR,
                message = "网络请求超时"
            )

            is UnresolvedAddressException -> ResultModel(code = CustomResultCode.CONNECTION_ERROR, message = "无法连接到服务器")

            is SerializationException -> ResultModel(code = CustomResultCode.SERIALIZATION_ERROR, message = "数据解析异常: ${e.message}")

            // 对于服务器返回的非2xx错误，Ktor会抛出ResponseException
            is ResponseException -> ResultModel(code = e.response.status.value, message = "服务器错误: ${e.response.status.description}")

            else -> ResultModel(code = CustomResultCode.UNKNOWN_ERROR, message = "未知网络异常: ${e.message}")
        }
    }

    suspend fun uploadFile(file: File, timeoutMillis: Long = 300_000L): ResultModel<JsonObject> {
        return try {
            // 利用 DefaultRequest 插件自动拼接 host 和 port
            val urlPath = config.uploadFilePath
            val bucketName = config.bucketName

            val response = httpClient.submitFormWithBinaryData(
                url = "$urlPath?bucketName=$bucketName", formData = formData {
                    append("file", file.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "image/png")
                        append(HttpHeaders.ContentDisposition, "filename=${file.name}")
                    })
                }) {
                timeout {
                    requestTimeoutMillis = timeoutMillis
                }
                // Auth 插件会自动添加 Token 和 tenant，这里不再需要
                headers.append(HttpHeaders.Connection, "close")
            }.bodyAsText()

            Log.d("POST", "path: $urlPath, response: $response")
            // 这里假设上传成功返回的也是 ResultModel 结构
            json.decodeFromString<ResultModel<JsonObject>>(response)
        } catch (e: Exception) {
            handleException(e)
        }
    }

    /**
     * 下载文件实现
     * @param path 文件的 URL。
     * @param filePath 文件保存的本地路径。
     * @param timeoutMillis 超时时间，单位毫秒。默认20分钟
     * @param onProgress 进度回调，返回当前进度、速度和剩余时间。
     * @return ResultModel<String> 包含下载成功后的文件路径或错误信息。
     */
    suspend fun downloadFile(
        path: String,
        filePath: String,
        timeoutMillis: Long = 1_200_000L,
        onProgress: (progress: Float, speed: String, remainingTime: String) -> Unit,
    ): ResultModel<String> {
        return withContext(Dispatchers.IO) {
            val file = File(filePath)
            // 确保文件所在目录存在
            file.parentFile?.mkdirs()
            val outputStream = file.outputStream()
            try {
                httpClient.prepareGet(path) { // 使用 httpUtil.httpClient 来执行网络请求
                    timeout {
                        requestTimeoutMillis = timeoutMillis
                    }
                }.execute { httpResponse ->
                    val totalBytes = httpResponse.contentLength() ?: 0L // 获取文件总大小
                    var bytesRead = 0L // 已读取的字节数
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE) // 缓冲区
                    val channel = httpResponse.bodyAsChannel() // 获取响应的字节通道

                    val startTime = System.currentTimeMillis() // 开始下载时间
                    var lastUpdateTime = startTime // 上次更新时间

                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer) // 从通道读取字节到缓冲区
                        if (read == -1) break // 读取结束
                        outputStream.write(buffer, 0, read) // 将缓冲区内容写入文件
                        bytesRead += read // 更新已读取字节数

                        val currentTime = System.currentTimeMillis() // 当前时间
                        // 每 500 毫秒更新一次进度，或者在下载完成时立即更新
                        if (currentTime - lastUpdateTime >= 500 || bytesRead == totalBytes || bytesRead == totalBytes - 1) {
                            val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                            val elapsedTime = (currentTime - startTime).toFloat() / 1000 // 已用时间 (秒)
                            val speed = if (elapsedTime > 0) (bytesRead / elapsedTime / (1024 * 1024)).format(2) else "0.00" // 速度 (MB/s)

                            // 估算剩余时间
                            val remainingTimeSeconds = if (bytesRead > 0) (elapsedTime / bytesRead) * (totalBytes - bytesRead) else 0f
                            val remainingTimeFormatted = formatTime(remainingTimeSeconds.toLong())

                            onProgress(progress, "$speed MB/s", remainingTimeFormatted)
                            lastUpdateTime = currentTime
                        }
                    }
                }
                outputStream.close() // 关闭输出流
                ResultModel.Companion.success(filePath) // 下载成功，返回文件路径
            } catch (e: Exception) {
                // 下载过程中发生异常，返回失败结果
                handleException(e)
            }
        }
    }

    /**
     * 格式化浮点数为指定位数的小数
     */
    private fun Float.format(digits: Int) = "%.${digits}f".format(this)

    /**
     * 格式化秒数为易读的时间字符串（例如 "2分10秒"）
     */
    private fun formatTime(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0秒"
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
    }

}

fun createLoginModel(json: Json, style: LoginKeyStyle, username: String, password: String): String {
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

fun createDefaultHttpClient(config: NetworkConfigProvider, json: Json): HttpClient {
    return HttpClient(CIO) {
        installPlugins(config, json)
    }
}

fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installPlugins(config: NetworkConfigProvider, json: Json) {
    // 插件1：内容协商，用于自动序列化/反序列化 @Serializable 类
    install(ContentNegotiation) {
        json(json)
    }

    // 插件2: 拦截相应根据code401修改状态码
    install(CustomAuthTriggerPlugin) {
        this.json = json
    }

    // 插件3：超时设置
    install(HttpTimeout) {
        requestTimeoutMillis = 30000
        connectTimeoutMillis = 30000
        socketTimeoutMillis = 30000
    }

    // 插件4：认证，自动处理 Bearer Token
    install(Auth) {
        bearer {
            // 设置 realm，这对于 Auth 插件正确识别和响应 401 挑战是必要的
            realm = "Access to protected resources"
            
            // 关键：每次请求前动态加载 token，而不是使用缓存
            // 这里使用 lambda 确保每次都读取最新的 config.token 值
            loadTokens {
                // 此块只在需要时被调用，且结果会被缓存
                // 但我们后面会让 refreshTokens 来处理 token 更新
                val token = config.token
                if (token.isNotEmpty()) {
                    println("Ktor Auth: loadTokens 返回现有 token")
                    BearerTokens(accessToken = token, refreshToken = null)
                } else {
                    println("Ktor Auth: loadTokens 返回 null (无已保存的 token)")
                    null
                }
            }
            
            // 只对非登录请求发送 Authorization 头
            // 当 token 为空时也发送请求（让服务器返回 401 来触发 refreshTokens）
            sendWithoutRequest { request ->
                val isLoginPath = request.url.encodedPath == config.loginPath
                val hasToken = config.token.isNotEmpty()
                // 对登录请求不发送 token，对其他请求：有 token 时才发送
                !isLoginPath && hasToken
            }

            refreshTokens {
                // 使用全局锁来确保只有一个刷新操作在执行
                AuthRefreshLock.mutex.withLock {
                    // `oldTokens` 是导致本次请求失败的旧 Token（首次登录后可能为 null）
                    val oldTokens = this.oldTokens
                    // `currentSavedToken` 是我们共享配置中当前存储的 Token
                    val currentSavedToken = config.token
                    
                    // 调试日志
                    val oldTokenPreview = oldTokens?.accessToken?.let { 
                        if (it.length > 16) "${it.take(6)}...${it.takeLast(6)}" else it 
                    } ?: "null"
                    val savedTokenPreview = if (currentSavedToken.length > 16) 
                        "${currentSavedToken.take(6)}...${currentSavedToken.takeLast(6)}" 
                    else currentSavedToken.ifEmpty { "empty" }
                    println("Ktor Auth: refreshTokens - oldToken: $oldTokenPreview, savedToken: $savedTokenPreview, 相同: ${oldTokens?.accessToken == currentSavedToken}")

                    // 关键检查：在我们等待锁的过程中，Token 是否已经被其他请求刷新了？
                    // 检查条件：
                    // 1. currentSavedToken 必须非空（有有效的 token）
                    // 2. currentSavedToken 和导致失败的旧 Token 不一样
                    // 满足这两个条件说明 token 已被更新（可能是外部登录或其他请求刷新的）
                    if (currentSavedToken.isNotEmpty() && oldTokens?.accessToken != currentSavedToken) {
                        println("Ktor Auth: Token 已被更新（外部登录或并发刷新），直接使用新 Token。")
                        BearerTokens(accessToken = currentSavedToken, refreshToken = null)
                    } else {
                        // 如果 Token 还是旧的，说明我们是第一个拿到锁并需要执行刷新的请求。
                        println("Ktor Auth: Token 已过期，开始执行刷新操作...")
                        val loginPayload = createLoginModel(json, config.getLoginKeyStyle(), config.username, config.password)

                        // 执行实际的登录（刷新）请求
                        val response = client.post(config.loginPath) {
                            markAsRefreshTokenRequest()
                            setBody(loginPayload)
                            contentType(ContentType.Application.Json)
                        }

                        val loginResult = response.body<ResultModel<UserToken>>()
                        if (loginResult.isSuccess() && loginResult.token != null) {
                            val newToken = loginResult.token.token
                            val newTenant = loginResult.token.tenant
                            // 更新本地存储的 Token
                            config.onNewTokenReceived(newToken, newTenant)
                            // 记录部分 token 用于调试（只显示前后几个字符）
                            val tokenPreview = if (newToken.length > 20) 
                                "${newToken.take(8)}...${newToken.takeLast(8)}" 
                            else newToken
                            println("Ktor Auth: Token 刷新成功，新 token: $tokenPreview")
                            // 返回新的 BearerTokens，Auth 插件会用它来重试
                            BearerTokens(accessToken = newToken, refreshToken = null)
                        } else {
                            // 如果重登录失败，则返回 null，原始请求将最终失败
                            println("Ktor Auth: 重新登录失败。")
                            null
                        }
                    }
                }
            }
        }
    }

    // 插件5：默认请求配置
    install(DefaultRequest) {
        val host = config.serverAddress.removePrefix("https://").removePrefix("http://")
        val protocol = if (config.serverAddress.startsWith("https")) URLProtocol.HTTPS else URLProtocol.HTTP
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

    // 插件6：日志
    install(Logging) {
        logger = Logger.ANDROID
        level = LogLevel.ALL
    }
}
