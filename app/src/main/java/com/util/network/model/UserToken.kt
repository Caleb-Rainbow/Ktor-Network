package com.util.network.model

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
    val officeId:Int?= null,
    @SerialName("tenant")
    val tenant:String?= null,
)
