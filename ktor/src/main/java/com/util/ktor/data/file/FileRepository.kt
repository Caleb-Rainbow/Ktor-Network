package com.util.ktor.data.file

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfig
import java.io.File

/**
 * @description
 * @author 杨帅林
 * @create 2024/11/12 8:29
 **/
class FileRepository(private val httpUtil: HttpUtil,private val config: NetworkConfig) {
    suspend fun uploadFile(file:File) = httpUtil.uploadFile(file, config)
}