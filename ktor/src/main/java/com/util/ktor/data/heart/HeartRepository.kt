package com.util.ktor.data.heart

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfig
import com.util.ktor.model.ResultModel

/**
 * @description
 * @author 杨帅林
 * @create 2025/8/19 14:44
 **/
class HeartRepository(
    private val httpUtil: HttpUtil,
    private val config: NetworkConfig
) {
    suspend fun heartbeat(deviceNumber: String, second: Int): ResultModel<String> =
        httpUtil.get(path = config.heartBeatPath + "/" + deviceNumber + "/" + second)
}