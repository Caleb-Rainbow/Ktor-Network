package com.util.ktor.config

interface NetworkConfigProvider {
    val serverAddress: String
    val serverPort: String
    var token: String
    val tenant: String
    val username: String
    val password: String
    val loginPath: String
    val uploadFilePath: String
    val checkUpdatePath: String
    val heartBeatPath: String
    val bucketName: String

    val getLogoPath: String
        get() = "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"

    val requestTimeoutMillis: Long
        get() = 300_000L

    val connectTimeoutMillis: Long
        get() = 300_000L

    val socketTimeoutMillis: Long
        get() = 300_000L

    fun onNewTokenReceived(token: String, tenant: String?)

    fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
}
