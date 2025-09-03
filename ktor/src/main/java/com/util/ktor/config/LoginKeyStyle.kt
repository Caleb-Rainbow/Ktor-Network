package com.util.ktor.config

/**
 * 定义登录请求体中 key 的命名风格
 */
enum class LoginKeyStyle {
    /** 风格1: { "userName": "...", "passWord": "..." } */
    CAMEL_CASE_V1,

    /** 风格2: { "username": "...", "password": "..." } */
    LOWER_CASE_V2
}