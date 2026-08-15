package com.loves.doudizhu

import com.loves.doudizhu.network.UpdateResponseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateResponseParserTest {
    @Test
    fun parsesWrappedUpdateAndFiltersBlankNotes() {
        val update = UpdateResponseParser.parse(
            JSONObject(
                """
                {
                  "ok": true,
                  "data": {
                    "versionCode": 3,
                    "versionName": "1.2.0",
                    "apkUrl": "https://example.com/app.apk",
                    "sha256": "${"A".repeat(64)}",
                    "forceUpdate": true,
                    "releaseNotes": ["新增自动更新", "", "  优化联机  "]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(3, update.versionCode)
        assertEquals("1.2.0", update.versionName)
        assertEquals("a".repeat(64), update.sha256)
        assertTrue(update.forceUpdate)
        assertEquals(listOf("新增自动更新", "优化联机"), update.releaseNotes)
    }

    @Test
    fun parsesFlatUpdateWithDefaults() {
        val update = UpdateResponseParser.parse(
            JSONObject(
                """
                {
                  "versionCode": 2,
                  "versionName": "1.1.0",
                  "apkUrl": "http://127.0.0.1/app.apk",
                  "sha256": "${"b".repeat(64)}"
                }
                """.trimIndent()
            )
        )

        assertFalse(update.forceUpdate)
        assertTrue(update.releaseNotes.isEmpty())
    }

    @Test
    fun rejectsInvalidDownloadUrl() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UpdateResponseParser.parse(
                JSONObject(
                    """
                    {
                      "versionCode": 2,
                      "versionName": "1.1.0",
                      "apkUrl": "file:///tmp/app.apk",
                      "sha256": "${"c".repeat(64)}"
                    }
                    """.trimIndent()
                )
            )
        }

        assertEquals("服务器未返回有效下载地址", error.message)
    }

    @Test
    fun rejectsInvalidSha256() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UpdateResponseParser.parse(
                JSONObject(
                    """
                    {
                      "versionCode": 2,
                      "versionName": "1.1.0",
                      "apkUrl": "https://example.com/app.apk",
                      "sha256": "not-a-hash"
                    }
                    """.trimIndent()
                )
            )
        }

        assertEquals("服务器未返回有效安装包校验值", error.message)
    }
}
