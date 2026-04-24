package com.util.ktor.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultModelTest {

    //region isSuccess / isError

    @Test
    fun `isSuccess returns true when code is 200`() {
        val model = ResultModel<String>(code = 200, message = "ok", data = "test")
        assertTrue(model.isSuccess())
        assertFalse(model.isError())
    }

    @Test
    fun `isError returns true for all non-200 codes`() {
        val errorCodes = listOf(201, 204, 301, 400, 401, 403, 404, 500, 501, 502, 503, 900, 901)
        for (code in errorCodes) {
            val model = ResultModel<String>(code = code, message = "error")
            assertTrue(model.isError(), "Expected isError for code $code")
            assertFalse(model.isSuccess(), "Expected !isSuccess for code $code")
        }
    }

    @Test
    fun `isSuccess with code zero`() {
        val model = ResultModel<String>(code = 0, message = "ok")
        assertFalse(model.isSuccess())
        assertTrue(model.isError())
    }

    @Test
    fun `isSuccess with negative code`() {
        val model = ResultModel<String>(code = -1, message = "ok")
        assertFalse(model.isSuccess())
        assertTrue(model.isError())
    }

    //endregion

    //region Companion factory methods

    @Test
    fun `success factory creates model with code 200 and provided data`() {
        val model = ResultModel.success("hello")
        assertEquals(200, model.code)
        assertEquals("成功", model.message)
        assertEquals("hello", model.data)
        assertNull(model.rows)
        assertNull(model.total)
        assertNull(model.url)
    }

    @Test
    fun `success factory with null-compatible data`() {
        val model = ResultModel.success<String?>(null)
        assertEquals(200, model.code)
        assertNull(model.data)
    }

    @Test
    fun `error factory creates model with ERROR code and message`() {
        val model: ResultModel<String> = ResultModel.error("自定义错误")
        assertEquals(ResultCodeType.ERROR.code, model.code)
        assertEquals("自定义错误", model.message)
        assertNull(model.data)
    }

    @Test
    fun `error factory with default message`() {
        val model: ResultModel<String> = ResultModel.error()
        assertEquals("未知错误", model.message)
    }

    //endregion

    //region ResultCodeType enum coverage

    @Test
    fun `ResultCodeType OK code is 200`() {
        assertEquals(200, ResultCodeType.OK.code)
    }

    @Test
    fun `ResultCodeType NO_LOGIN code is 401`() {
        assertEquals(401, ResultCodeType.NO_LOGIN.code)
    }

    @Test
    fun `ResultCodeType NO_PERMISSION code is 403`() {
        assertEquals(403, ResultCodeType.NO_PERMISSION.code)
    }

    @Test
    fun `ResultCodeType NOT_FOUND code is 404`() {
        assertEquals(404, ResultCodeType.NOT_FOUND.code)
    }

    @Test
    fun `ResultCodeType INTERNAL_SERVER_ERROR code is 500`() {
        assertEquals(500, ResultCodeType.INTERNAL_SERVER_ERROR.code)
    }

    @Test
    fun `ResultCodeType ERROR code is 501`() {
        assertEquals(501, ResultCodeType.ERROR.code)
    }

    @Test
    fun `ResultCodeType has exactly 6 values`() {
        assertEquals(6, ResultCodeType.entries.size)
    }

    //endregion

    //region CustomResultCode constants

    @Test
    fun `CustomResultCode UNKNOWN_ERROR is 900`() {
        assertEquals(900, CustomResultCode.UNKNOWN_ERROR)
    }

    @Test
    fun `CustomResultCode TIMEOUT_ERROR is 901`() {
        assertEquals(901, CustomResultCode.TIMEOUT_ERROR)
    }

    @Test
    fun `CustomResultCode CONNECTION_ERROR is 902`() {
        assertEquals(902, CustomResultCode.CONNECTION_ERROR)
    }

    @Test
    fun `CustomResultCode SERIALIZATION_ERROR is 903`() {
        assertEquals(903, CustomResultCode.SERIALIZATION_ERROR)
    }

    //endregion

    //region Serialization edge cases

    @Test
    fun `deserialize ResultModel with null optional fields`() {
        val json = """{"code":200,"msg":"ok"}"""
        val model = Json.decodeFromString<ResultModel<String>>(json)
        assertEquals(200, model.code)
        assertEquals("ok", model.message)
        assertNull(model.data)
        assertNull(model.rows)
        assertNull(model.total)
        assertNull(model.url)
    }

    @Test
    fun `deserialize ResultModel with all fields populated`() {
        val json = """{
            "code":200,
            "msg":"success",
            "data":"testData",
            "rows":["row1","row2"],
            "total":10,
            "url":"https://example.com"
        }""".trimIndent()
        val model = Json.decodeFromString<ResultModel<String>>(json)
        assertEquals(200, model.code)
        assertEquals("success", model.message)
        assertEquals("testData", model.data)
        assertEquals(listOf("row1", "row2"), model.rows)
        assertEquals(10, model.total)
        assertEquals("https://example.com", model.url)
    }

    @Test
    fun `deserialize ResultModel with rows and total for list response`() {
        val json = """{
            "code":200,
            "msg":"ok",
            "rows":[1,2,3],
            "total":3
        }"""
        val model = Json.decodeFromString<ResultModel<Int>>(json)
        assertNull(model.data)
        assertEquals(listOf(1, 2, 3), model.rows)
        assertEquals(3, model.total)
    }

    @Test
    fun `deserialize ResultModel with error code and null data`() {
        val json = """{"code":500,"msg":"Internal Server Error"}"""
        val model = Json.decodeFromString<ResultModel<String>>(json)
        assertTrue(model.isError())
        assertNull(model.data)
    }

    @Test
    fun `deserialize ResultModel msg field maps to message property`() {
        val json = """{"code":200,"msg":"操作成功","data":"result"}"""
        val model = Json.decodeFromString<ResultModel<String>>(json)
        assertEquals("操作成功", model.message)
    }

    @Test
    fun `deserialize ResultModel with empty data string`() {
        val json = """{"code":200,"msg":"ok","data":""}"""
        val model = Json.decodeFromString<ResultModel<String>>(json)
        assertEquals("", model.data)
    }

    @Test
    fun `deserialize ResultModel with empty rows array`() {
        val json = """{"code":200,"msg":"ok","rows":[]}"""
        val model = Json.decodeFromString<ResultModel<String>>(json)
        assertEquals(emptyList<String>(), model.rows)
    }

    @Test
    fun `deserialize ResultModel with total zero`() {
        val json = """{"code":200,"msg":"ok","total":0}"""
        val model = Json.decodeFromString<ResultModel<String>>(json)
        assertEquals(0, model.total)
    }

    @Test
    fun `round trip serialization preserves data`() {
        val json = Json { ignoreUnknownKeys = true }
        val original = ResultModel(
            code = 200,
            message = "成功",
            data = "hello",
            rows = listOf("a", "b"),
            total = 2,
            url = "https://example.com"
        )
        val serialized = json.encodeToString(serializer<ResultModel<String>>(), original)
        val deserialized = json.decodeFromString(serializer<ResultModel<String>>(), serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `ResultModel with nullable Int data`() {
        val json = """{"code":200,"msg":"ok","data":42}"""
        val model = Json.decodeFromString<ResultModel<Int>>(json)
        assertEquals(42, model.data)
    }

    @Test
    fun `ResultModel with boolean data`() {
        val json = """{"code":200,"msg":"ok","data":true}"""
        val model = Json.decodeFromString<ResultModel<Boolean>>(json)
        assertEquals(true, model.data)
    }

    //endregion

    //region Data class behavior

    @Test
    fun `data class copy works correctly`() {
        val original = ResultModel.success("data")
        val copied = original.copy(code = 500, message = "error")
        assertEquals(500, copied.code)
        assertEquals("error", copied.message)
        assertEquals("data", copied.data)
        assertEquals(200, original.code)
    }

    @Test
    fun `data class equality works correctly`() {
        val model1 = ResultModel(code = 200, message = "ok", data = "test")
        val model2 = ResultModel(code = 200, message = "ok", data = "test")
        assertEquals(model1, model2)
    }

    @Test
    fun `data class inequality with different code`() {
        val model1 = ResultModel<String>(code = 200, message = "ok")
        val model2 = ResultModel<String>(code = 500, message = "ok")
        assert(model1 != model2)
    }

    //endregion
}
