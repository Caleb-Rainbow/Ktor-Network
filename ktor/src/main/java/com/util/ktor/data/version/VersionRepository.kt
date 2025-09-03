package com.util.ktor.data.version

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfig
import com.util.ktor.data.version.model.Version
import com.util.ktor.model.ResultModel

/**
 * @description
 * @author 杨帅林
 * @create 2024/11/23 10:56
 **/
class VersionRepository(private val httpUtil: HttpUtil,private val config: NetworkConfig) {
    suspend fun checkUpdate():ResultModel<Version> = httpUtil.get(path = config.checkUpdatePath)
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
    ) = httpUtil.downloadFile(path, filePath, onProgress)
}