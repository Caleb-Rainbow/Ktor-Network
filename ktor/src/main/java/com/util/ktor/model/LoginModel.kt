package com.util.ktor.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @description
 * @author 杨帅林
 * @create 2025/9/2 17:15
 **/
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LoginModel(
    @SerialName("userName")
    val username: String,
    @SerialName("passWord")
    val password: String,
)