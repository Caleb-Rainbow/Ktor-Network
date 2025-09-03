package com.util.ktor.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

/**
 * @description
 * @author 杨帅林
 * @create 2025/8/16 13:55
 **/
object BigDecimalSerializer : KSerializer<BigDecimal> {

    // 1. 描述符：告诉 kotlinx-serialization 这个类型将被序列化为 String
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    /**
     * 序列化方法：将 BigDecimal 对象转换为 String
     */
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        // 使用 toPlainString() 来确保数值被表示为不带科学计数法的普通字符串
        // 例如：1.0E-10 会被转换为 "0.0000000001"
        encoder.encodeString(value.toPlainString())
    }

    /**
     * 反序列化方法：将 String 转换回 BigDecimal 对象
     */
    override fun deserialize(decoder: Decoder): BigDecimal {
        // 从解码器中读取字符串，然后构造 BigDecimal
        return BigDecimal(decoder.decodeString())
    }
}