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
}
