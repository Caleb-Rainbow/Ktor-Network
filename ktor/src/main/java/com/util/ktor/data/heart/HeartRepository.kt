package com.util.ktor.data.heart

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.model.ResultModel
import java.net.URLEncoder

/**
 * @description
 * @author 杨帅林
 * @create 2025/8/19 14:44
 **/
class HeartRepository(
    private val httpUtil: HttpUtil,
    private val config: NetworkConfigProvider
) {
    suspend fun heartbeat(deviceNumber: String, second: Int): ResultModel<String> {
        require(deviceNumber.isNotBlank()) { "设备编号不能为空" }
        require(second > 0) { "心跳间隔必须为正数" }
        val encodedDeviceNumber = URLEncoder.encode(deviceNumber, "UTF-8")
        val basePath = config.heartBeatPath.trimEnd('/')
        return httpUtil.get(path = "$basePath/$encodedDeviceNumber/$second")
    }
}