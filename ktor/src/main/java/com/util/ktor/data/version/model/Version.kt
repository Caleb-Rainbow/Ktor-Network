package com.util.ktor.data.version.model

import kotlinx.serialization.Serializable

/**
  * @description 软件版本更新信息类
  * @author 杨帅林
  * @create 2024/7/10 21:19
 **/
@Serializable
data class Version(
    /**app名称*/
    val appName:String,
    /**版本号*/
    val code:Int,
    /**版本名称*/
    val name:String,
    /**下载链接*/
    val url:String,
    /**文件大小*/
    val size:String?,
    /**更新描述*/
    val description:String
)
