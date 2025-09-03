package com.util.ktor

import android.util.Log
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfig
import com.util.ktor.model.ResultCodeType
import com.util.ktor.model.ResultModel
import com.util.ktor.model.UserToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
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
import io.ktor.http.path
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
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
    val config: NetworkConfig,
) {
    suspend fun <T> makeRequest(
        serializer: KSerializer<ResultModel<T>>,
        method: HttpMethod,
        path: String,
        body: Any? = null,
        contentType: ContentType = ContentType.Application.Json,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        try {
            val localHost = config.serverAddress
            val localPort = config.serverPort.toIntOrNull()
            val token = config.token
            val tenant = config.tenant
            val response = when (method) {
                HttpMethod.Post -> httpClient.post {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap,
                        body
                    )
                }.bodyAsText()

                HttpMethod.Get -> httpClient.get {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap
                    )
                }.bodyAsText()

                HttpMethod.Delete -> httpClient.delete {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap
                    )
                }.bodyAsText()

                HttpMethod.Put -> httpClient.put {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap,
                        body
                    )
                }.bodyAsText()

                else -> ""
            }
            Log.d(method.value, "path: $path  response: $response")
            val result = json.decodeFromString(serializer, response)
            if (result.code == ResultCodeType.NO_LOGIN.code && path != config.loginPath) {
                val loginPayload = createLoginModel(config.getLoginKeyStyle(), config.username, config.password)
                val r = httpClient.post {
                    url {
                        this.protocol = if (localHost.startsWith("https")) URLProtocol.HTTPS else URLProtocol.HTTP
                        this.host = localHost.removePrefix("https://").removePrefix("http://")
                        localPort?.let {
                            this.port = it
                        }
                        path(config.loginPath)
                        setBody(loginPayload)
                    }
                    contentType(ContentType.Application.Json)
                }.bodyAsText()
                Log.d("Post", "makeRequest: 重新登录  path: ${config.loginPath}  response: $r")
                val loginResult = json.decodeFromString<ResultModel<UserToken>>(r)
                if (loginResult.isSuccess()) {
                    loginResult.token?.let {
                        config.onNewTokenReceived(it.token, it.tenant)
                    }
                    return makeRequest(serializer, method, path, body, contentType, headersMap, parametersMap)
                } else {
                    return ResultModel.Companion.error(message = loginResult.message ?: "登录失败")
                }
            } else {
                return result
            }

        } catch (e: Exception) {
            return handleException(e, path)
        }
    }

    private fun setupRequest(
        token: String,
        host: String,
        port: Int?,
        tenant: String,
        contentType: ContentType,
        requestBuilder: HttpRequestBuilder,
        path: String,
        headersMap: Map<String, String>,
        parametersMap: Map<String, String>,
        body: Any? = null,
    ) {
        // 设置请求头
        headersMap.forEach {
            requestBuilder.headers.append(it.key, it.value)
        }

        if (token.isNotEmpty()) {
            requestBuilder.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }
        if (tenant.isNotEmpty()) {
            requestBuilder.headers.append("tenant", tenant)
        }
        if (!path.contains("http")) {
            // 设置 URL 和参数
            requestBuilder.url {
                this.protocol = if (host.startsWith("https")) URLProtocol.HTTPS else URLProtocol.HTTP
                this.host = host.split("//")[1]
                port?.let {
                    this.port = it
                }
                path(path)
                parametersMap.forEach {
                    parameters.append(it.key, it.value)
                }
                // 设置请求体
                body?.let {
                    requestBuilder.setBody(body)
                }
            }
        } else {
            requestBuilder.url(path)
            requestBuilder.url {
                parametersMap.forEach {
                    parameters.append(it.key, it.value)
                }
                // 设置请求体
                body?.let {
                    requestBuilder.setBody(body)
                }
            }
        }
        requestBuilder.contentType(contentType)
    }

    private fun <T> handleException(e: Exception, path: String): ResultModel<T> {
        Log.e("http", "Error in path: $path \n" + e.stackTraceToString())
        return when (e) {
            is HttpRequestTimeoutException -> ResultModel(ResultCodeType.ERROR.code, "网络请求超时")
            is ConnectTimeoutException -> ResultModel(ResultCodeType.ERROR.code, "网络连接超时")
            is SerializationException -> ResultModel(ResultCodeType.ERROR.code, "数据解析异常")
            else -> ResultModel(ResultCodeType.ERROR.code, "网络连接异常")
        }
    }

    // POST 请求
    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Post,
                path,
                body,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }

    // GET 请求
    suspend inline fun <reified T> get(
        path: String,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Get,
                path,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }

    // DELETE 请求
    suspend inline fun <reified T> delete(
        path: String,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Delete,
                path,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }

    // PUT 请求
    suspend inline fun <reified T> put(
        path: String,
        body: String = "",
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Put,
                path,
                body,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }


    suspend fun uploadFile(file: File, config: NetworkConfig): ResultModel<JsonObject> {
        return withContext(Dispatchers.IO) {
            val url = kotlin.text.StringBuilder().append(config.serverAddress)
            if (config.serverPort.isNotEmpty()) url.append(":").append(config.serverPort)
            url.append(config.uploadFilePath)
            //存储桶名称
            url.append("?bucketName=scaleErp")
            val result = httpClient.submitFormWithBinaryData(url = url.toString(), formData = formData {
                append("file", file.readBytes(), headers = Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=${file.name}")
                })
            }) {
                if (config.token.isNotEmpty()) {
                    headers.append(HttpHeaders.Authorization, "Bearer ${config.token}")
                }
                if (config.tenant.isNotEmpty()) {
                    headers.append("tenant", config.tenant)
                }
                headers.append(HttpHeaders.Connection, "close")
            }.bodyAsText()
            Log.d("POST", "path: ${config.uploadFilePath}  response: $result")
            json.decodeFromString(result)
        }
    }

    /**
     * 下载文件实现
     * @param path 文件的 URL。
     * @param filePath 文件保存的本地路径。
     * @param onProgress 进度回调，返回当前进度、速度和剩余时间。
     * @return ResultModel<String> 包含下载成功后的文件路径或错误信息。
     */
    suspend fun downloadFile(
        path: String,
        filePath: String,
        onProgress: (progress: Float, speed: String, remainingTime: String) -> Unit
    ): ResultModel<String> {
        return withContext(Dispatchers.IO) {
            val file = File(filePath)
            // 确保文件所在目录存在
            file.parentFile?.mkdirs()
            val outputStream = file.outputStream()
            try {
                httpClient.prepareGet(path) { // 使用 httpUtil.httpClient 来执行网络请求
                    timeout {
                        requestTimeoutMillis = 1200000
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
                ResultModel.error(e.localizedMessage ?: "下载失败")
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

fun createLoginModel(style: LoginKeyStyle, username: String, password: String) = buildJsonObject {
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
}

fun createDefaultHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
    }
}