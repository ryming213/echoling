package com.echoling.app.data.local.db

import com.echoling.app.domain.model.AutoSubtitleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSubtitleStatusTest {

    @Test
    fun `enum ↔ DB string round-trip for all 4 values`() {
        for (s in AutoSubtitleStatus.entries) {
            val dbString = s.dbValue
            val parsed = AutoSubtitleStatus.fromDbString(dbString)
            assertEquals(s, parsed)
        }
    }

    @Test
    fun `null ↔ null distinction is preserved`() {
        assertNull(AutoSubtitleStatus.fromDbString(null))
        assertFalse(AutoSubtitleStatus.entries.any { it.dbValue == "" })
    }

    @Test
    fun `unknown db string returns null instead of crashing`() {
        assertNull(AutoSubtitleStatus.fromDbString("UNKNOWN_STATE"))
    }

    @Test
    fun `hasAutoSubtitleIssue returns true for PENDING IN_PROGRESS FAILED`() {
        assertTrue(AutoSubtitleStatus.PENDING.hasAutoSubtitleIssue)
        assertTrue(AutoSubtitleStatus.IN_PROGRESS.hasAutoSubtitleIssue)
        assertTrue(AutoSubtitleStatus.FAILED.hasAutoSubtitleIssue)
        assertFalse(AutoSubtitleStatus.READY.hasAutoSubtitleIssue)
    }
}