package com.util.ktor.data.personalization

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.personalization.model.LogoModel
import com.util.ktor.model.ResultModel
import java.net.URLEncoder

/**
 * @description
 * @author 杨帅林
 * @create 2025/9/10 10:18
 **/
class PersonalizationRepository(private val httpUtil: HttpUtil, private val config: NetworkConfigProvider) {
    suspend fun getLogoUrl(deviceNumber: String): ResultModel<LogoModel> {
        require(deviceNumber.isNotBlank()) { "设备编号不能为空" }
        val encodedDeviceNumber = URLEncoder.encode(deviceNumber, "UTF-8")
        val basePath = config.getLogoPath.trimEnd('/')
        return httpUtil.get(path = "$basePath/$encodedDeviceNumber")
    }
}