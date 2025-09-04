package com.util.ktor.model

object CustomResultCode {
    const val UNKNOWN_ERROR = 900 // 未知错误
    const val TIMEOUT_ERROR = 901 // 网络请求超时
    const val CONNECTION_ERROR = 902 // 网络连接异常 (如DNS解析失败)
    const val SERIALIZATION_ERROR = 903 // 数据解析异常
}