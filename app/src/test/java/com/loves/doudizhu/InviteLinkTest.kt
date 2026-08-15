package com.loves.doudizhu

import com.loves.doudizhu.network.InviteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    @Test
    fun parsesSixDigitRoom() {
        assertEquals("123456", InviteLink.parse("friendddz://join?room=123456"))
    }

    @Test
    fun rejectsInvalidRoom() {
        assertNull(InviteLink.parse("friendddz://join?room=12345"))
    }

    @Test
    fun parsesPublicInviteUrl() {
        assertEquals(
            "123456",
            InviteLink.parse("http://114.55.142.169:18080/invite/123456")
        )
    }

    @Test
    fun createsClickablePublicInviteUrl() {
        assertEquals(
            "http://114.55.142.169:18080/invite/123456",
            InviteLink.createShareUrl("123456")
        )
    }
}
