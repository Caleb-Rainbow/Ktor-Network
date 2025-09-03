package com.util.ktor.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @description
 * @author 杨帅林
 * @create 2024/10/16 13:29
 **/
@Serializable
data class ResultModel<T>(
    val code: Int,
    @SerialName("msg")
    val message: String?,
    val data: T? = null,
    val rows:List<T>? = null,
    val total:Int? = null,
    @SerialName("img")
    val image:String? = null,
    val token: UserToken? = null,
    val uuid:String? = null,
    val url:String? = null,
){
    companion object{
        fun <T> error(message: String = "未知错误") = ResultModel<T>(code = ResultCodeType.ERROR.code,message = message)
        fun <T> success(data: T) = ResultModel(code = ResultCodeType.OK.code,message = "成功",data = data)
    }

    fun isSuccess():Boolean{
        return code == ResultCodeType.OK.code
    }

    fun isError():Boolean{
        return code != ResultCodeType.OK.code
    }
}


enum class ResultCodeType(val code: Int){
    OK(200),
    NO_LOGIN(401),
    NO_PERMISSION(403),
    NOT_FOUND(404),
    INTERNAL_SERVER_ERROR(500),
    ERROR(501),
}
