package com.loves.wangyouge

import com.loves.wangyouge.network.InviteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    @Test
    fun parsesSixDigitRoom() {
        assertEquals("123456", InviteLink.parse("wangyouge://join?room=123456"))
    }

    @Test
    fun rejectsInvalidRoom() {
        assertNull(InviteLink.parse("wangyouge://join?room=12345"))
        assertNull(InviteLink.parse("wangyouge://join?room=12ab3456"))
        assertNull(InviteLink.parse("wangyouge://join?room=1234567"))
        assertNull(InviteLink.parse("wangyouge://other?room=123456"))
        assertNull(InviteLink.parse("wangyouge://join?room=%31%32%33%34%35x"))
    }

    @Test
    fun parsesIntentUriUsedByInvitePage() {
        assertEquals(
            "123456",
            InviteLink.parse(
                "intent://join?room=123456#Intent;scheme=wangyouge;" +
                    "package=com.loves.wangyouge;end"
            )
        )
    }

    @Test
    fun parsesPublicInviteUrl() {
        assertEquals(
            "123456",
            InviteLink.parse("http://114.55.142.169:18080/invite/123456")
        )
        assertEquals(
            "654321",
            InviteLink.parse("https://games.example/invite/654321/?from=friend")
        )
        assertNull(InviteLink.parse("https://games.example/invite/123456/extra"))
    }

    @Test
    fun createsClickablePublicInviteUrl() {
        assertEquals(
            "http://114.55.142.169:18080/invite/123456",
            InviteLink.createShareUrl("123456")
        )
    }

    @Test
    fun refusesToCreateMalformedInviteLinks() {
        assertThrows(IllegalArgumentException::class.java) {
            InviteLink.create("12ab3456")
        }
        assertThrows(IllegalArgumentException::class.java) {
            InviteLink.createShareUrl("12345")
        }
    }
}
