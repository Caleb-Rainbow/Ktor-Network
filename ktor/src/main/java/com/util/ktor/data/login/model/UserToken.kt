package com.util.ktor.data.login.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * @description
 * @author 杨帅林
 * @create 2025/9/2 17:07
 **/
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserToken(
    val token: String,
    @JsonNames("deptId")
    val officeId: Int? = null,
    @SerialName("tenant")
    val tenant: String? = null,
    /*是否需要强制修改密码*/
    val forceChangePassword: Boolean? = null,
    /*强制改密原因*/
    val forceChangeReason: String? = null,
)