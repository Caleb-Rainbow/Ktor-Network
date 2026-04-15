package com.util.ktor.data.version

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.version.model.Version
import com.util.ktor.model.ResultModel
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.path
import kotlinx.coroutines.CancellationException

/**
 * @description
 * @author 杨帅林
 * @create 2024/11/23 10:56
 **/
class VersionRepository(
    private val httpUtil: HttpUtil,
    private val noAuthHttpClient: HttpClient,
    private val config: NetworkConfigProvider
) {
    /**
     * 检查更新，该接口不需要鉴权，使用无鉴权的 HttpClient
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
}