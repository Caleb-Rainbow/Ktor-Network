package com.util.ktor

import android.util.Log
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.model.CustomResultCode
import com.util.ktor.model.ResultModel
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.parameter
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
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.io.File

private const val TAG = "HttpUtil-Ktor"
private const val MAX_UPLOAD_FILE_SIZE_MB = 200L

class HttpUtil(
    val httpClient: HttpClient,
    val json: Json,
    val config: NetworkConfigProvider,
    private val downloadHttpClient: HttpClient? = null,
) {

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <T> executeRequest(
        serializer: KSerializer<ResultModel<T>>,
        block: HttpRequestBuilder.() -> Unit,
    ): ResultModel<T> {
        try {
            val response = httpClient.request {
                block()
                headers.append("X-Requested-With", "XMLHttpRequest")
            }
            val result = response.bodyAsChannel().toInputStream().use { stream ->
                json.decodeFromStream(serializer, stream)
            }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return handleException(e)
        }
    }

    /**
     * 执行 HTTP 请求并返回原始响应体字符串，适用于需要自行解析响应的场景。
     */
    suspend fun executeRawRequest(
        block: HttpRequestBuilder.() -> Unit,
    ): String {
        val response = httpClient.request {
            block()
            headers.append("X-Requested-With", "XMLHttpRequest")
        }
        return response.bodyAsText()
    }

    suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        parametersMap: Map<String, String> = emptyMap(),
    ): ResultModel<T> = executeRequest(serializer()) {
        this.method = method
        if (path.startsWith("http://") || path.startsWith("https://")) {
            url(path)
        } else {
            url { path(path) }
        }
        parametersMap.forEach { (key, value) -> parameter(key, value) }
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
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
        // CancellationException 必须由调用方重新抛出，此处仅作为防御性检查
        if (e is CancellationException) {
            throw e
        }
        if (config.isLogEnabled) {
            Log.e(TAG, e.stackTraceToString())
        }
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
            val fileSizeBytes = file.length()
            val maxSizeBytes = MAX_UPLOAD_FILE_SIZE_MB * 1024L * 1024L
            if (fileSizeBytes > maxSizeBytes) {
                return ResultModel.error("文件大小超过${MAX_UPLOAD_FILE_SIZE_MB}MB限制")
            }
            if (fileSizeBytes > 100L * 1024L * 1024L) {
                val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)
                Log.w(
                    TAG,
                    "上传文件较大: ${String.format("%.2f", fileSizeMB)}MB, 注意内存使用"
                )
            }

            val urlPath = config.uploadFilePath

            val response = httpClient.submitFormWithBinaryData(
                url = urlPath,
                formData = formData {
                    append("file", InputProvider { file.inputStream().asSource().buffered() }, Headers.build {
                        append(HttpHeaders.ContentType, file.contentType())
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=\"${file.name.replace("\"", "\\\"")}\""
                        )
                    })
                }
            ) {
                parameter("bucketName", config.bucketName)
                timeout {
                    requestTimeoutMillis = timeoutMillis
                }
                headers.append(HttpHeaders.Connection, "close")
                headers.append("X-Requested-With", "XMLHttpRequest")
            }.bodyAsText()

            if (config.isLogEnabled) Log.d(TAG, "uploadFile path: $urlPath")
            json.decodeFromString<ResultModel<JsonObject>>(response)
        } catch (e: CancellationException) {
            throw e
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
        return withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (file.canonicalPath != file.absolutePath) {
                return@withContext ResultModel.error<String>("非法文件路径")
            }
            if (filePath.contains("..")) {
                return@withContext ResultModel.error<String>("非法文件路径")
            }

            val parent = file.parentFile
            if (parent == null) {
                return@withContext ResultModel.error<String>("无效文件路径：无法获取父目录")
            }
            if (!parent.exists() && !parent.mkdirs()) {
                return@withContext ResultModel.error<String>("无法创建目录: ${parent.absolutePath}")
            }
            val fileExistedBefore = file.exists()
            file.outputStream().use { outputStream ->
                try {
                    (downloadHttpClient ?: httpClient).prepareGet(path) {
                        timeout {
                            requestTimeoutMillis = timeoutMillis
                        }
                        headers.append("X-Requested-With", "XMLHttpRequest")
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
                                bytesRead >= totalBytes
                            ) {
                                val progress =
                                    if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                                val elapsedTimeSeconds =
                                    (currentTime - startTime).toDouble() / 1000.0
                                val speed = if (elapsedTimeSeconds > 0)
                                    (bytesRead.toDouble() / elapsedTimeSeconds / (1024.0 * 1024.0)).format(2)
                                else
                                    "0.00"

                                val remainingTimeSeconds = if (bytesRead > 0)
                                    (elapsedTimeSeconds / bytesRead) * (totalBytes - bytesRead)
                                else
                                    0.0
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
                } catch (e: CancellationException) {
                    if (!fileExistedBefore) file.delete()
                    throw e
                } catch (e: Exception) {
                    if (!fileExistedBefore) file.delete()
                    handleException<String>(e)
                }
            }
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun formatTime(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0秒"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分${seconds}秒"
            minutes > 0 -> "${minutes}分${seconds}秒"
            else -> "${seconds}秒"
        }
    }
}
