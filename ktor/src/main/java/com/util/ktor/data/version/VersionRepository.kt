package com.util.ktor.data.version

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.version.model.Version
import com.util.ktor.model.CustomResultCode
import com.util.ktor.model.ResultModel
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout

class VersionRepository(
    private val httpUtil: HttpUtil,
    private val noAuthHttpClient: HttpClient,
    private val config: NetworkConfigProvider
) {
    companion object {
        private const val DUAL_CHECK_TIMEOUT_MS = 15_000L
        private const val DEFAULT_POLLING_INTERVAL_MS = 300_000L
    }

    /**
     * 检查更新，该接口不需要鉴权，使用无鉴权的 HttpClient。
     */
    suspend fun checkUpdate(): ResultModel<Version> {
        return try {
            val response = noAuthHttpClient.request {
                method = HttpMethod.Get
                val path = config.checkUpdatePath
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    url(path)
                } else {
                    url { path(path) }
                }
                headers.append("X-Requested-With", "XMLHttpRequest")
            }
            val body = response.bodyAsText()
            httpUtil.json.decodeFromString<ResultModel<Version>>(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            httpUtil.handleException(e)
        }
    }

    /**
     * 同时检查外部和内部网络的更新。
     * 并行发送两个请求，返回最先成功的结果；若第一个完成的失败，则等待第二个。
     * 双通道模式下每个请求有 15 秒超时，两个请求均失败时优先返回外网错误。
     */
    suspend fun checkUpdateDual(): UpdateCheckResult = coroutineScope {
        val externalJob = async { checkUpdateAt(config.checkUpdatePath, DUAL_CHECK_TIMEOUT_MS) }
        val internalJob = async { checkUpdateAt(resolveInternalUpdateUrl(), DUAL_CHECK_TIMEOUT_MS) }

        val (firstResult, isExternal, secondJob) = select<
            Triple<ResultModel<Version>, Boolean, Deferred<ResultModel<Version>>>> {
            externalJob.onAwait { Triple(it, true, internalJob) }
            internalJob.onAwait { Triple(it, false, externalJob) }
        }

        if (firstResult.isSuccess()) {
            secondJob.cancel()
            return@coroutineScope if (isExternal) {
                UpdateCheckResult.External(firstResult)
            } else {
                UpdateCheckResult.Internal(firstResult)
            }
        }

        val secondResult = try {
            secondJob.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            httpUtil.handleException(e)
        }

        if (secondResult.isSuccess()) {
            if (isExternal) UpdateCheckResult.Internal(secondResult)
            else UpdateCheckResult.External(secondResult)
        } else {
            UpdateCheckResult.External(if (isExternal) firstResult else secondResult)
        }
    }

    /**
     * 创建一个冷 Flow，定期检查更新并发射结果。
     * 首次收集时立即发射，之后按 [intervalMs] 间隔重复。
     * 收集者取消时自动停止轮询。
     *
     * @param intervalMs 轮询间隔（毫秒），默认 5 分钟
     * @param useDualNetwork 是否同时检查内外网，默认 false
     */
    fun checkUpdateFlow(
        intervalMs: Long = DEFAULT_POLLING_INTERVAL_MS,
        useDualNetwork: Boolean = true
    ): Flow<ResultModel<Version>> = flow {
        require(intervalMs > 0) { "轮询间隔必须大于0" }
        while (currentCoroutineContext().isActive) {
            val result = try {
                if (useDualNetwork) {
                    checkUpdateDual().result
                } else {
                    checkUpdate()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                httpUtil.handleException(e)
            }
            emit(result)
            delay(intervalMs)
        }
    }

    /**
     * 下载文件
     * @param path 文件的 URL。
     * @param filePath 文件保存的本地路径。
     * @param onProgress 进度回调，返回当前进度、速度和剩余时间。
     */
    suspend fun downloadFile(
        path: String,
        filePath: String,
        onProgress: (progress: Float, speed: String, remainingTime: String) -> Unit
    ) = httpUtil.downloadFile(path, filePath, onProgress = onProgress)

    private suspend fun checkUpdateAt(
        url: String,
        timeoutMs: Long = DUAL_CHECK_TIMEOUT_MS
    ): ResultModel<Version> {
        return try {
            withTimeout(timeoutMs) {
                val response = noAuthHttpClient.request {
                    method = HttpMethod.Get
                    url(url)
                    headers.append("X-Requested-With", "XMLHttpRequest")
                }
                val body = response.bodyAsText()
                httpUtil.json.decodeFromString<ResultModel<Version>>(body)
            }
        } catch (e: TimeoutCancellationException) {
            ResultModel(code = CustomResultCode.TIMEOUT_ERROR, message = "网络请求超时")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            httpUtil.handleException(e)
        }
    }

    /**
     * 从 checkUpdatePath 中提取路径部分，拼接 serverAddress:serverPort 构建内网 URL。
     * 例如：
     *   checkUpdatePath = "https://vis.xingchenwulian.com/system/appversion/checkUpdate/%E6%99%A8%E6%A3%80%E4%BB%AA"
     *   serverAddress = "http://192.168.0.8", serverPort = "8085"
     *   → "http://192.168.0.8:8085/system/appversion/checkUpdate/%E6%99%A8%E6%A3%80%E4%BB%AA"
     */
    private fun resolveInternalUpdateUrl(): String {
        val path = config.checkUpdatePath
        val relativePath = if (path.startsWith("http://") || path.startsWith("https://")) {
            val uri = java.net.URI(path)
            buildString {
                append(uri.rawPath)
                if (uri.rawQuery != null) append("?").append(uri.rawQuery)
                // Fragment 不适用于 API 端点，故不保留
            }.ifBlank { path }
        } else {
            path
        }

        val address = config.serverAddress.trimEnd('/')
        val hasEmbeddedPort = address.substringAfter("://", "").contains(":")
        val base = if (hasEmbeddedPort) address else "$address:${config.serverPort.trim()}"

        return "$base${if (relativePath.startsWith("/")) "" else "/"}$relativePath"
    }
}
