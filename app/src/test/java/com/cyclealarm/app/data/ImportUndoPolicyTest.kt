package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportUndoPolicyTest {

    @Test
    fun `only alarms created after the snapshot are cancelled`() {
        val cancelled = ImportUndoPolicy.idsToCancelAfterUndo(
            currentIds = listOf(1, 2, 3, 4),
            snapshotIds = listOf(1, 2)
        )
        assertEquals(listOf(3, 4), cancelled)
    }

    @Test
    fun `alarms present in the snapshot are never cancelled`() {
        val cancelled = ImportUndoPolicy.idsToCancelAfterUndo(
            currentIds = listOf(7, 8),
            snapshotIds = listOf(7, 8)
        )
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun `empty current set yields nothing to cancel`() {
        assertTrue(ImportUndoPolicy.idsToCancelAfterUndo(emptyList(), listOf(1)).isEmpty())
    }
}
