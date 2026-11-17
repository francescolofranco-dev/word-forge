package com.wordforge.ui.screens

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportErrorMessageTest {
    @Test
    fun unrelatedJsonExplainsWhichFileToChoose() {
        val error = runCatching {
            JSONObject("""{"notes": []}""").getJSONArray("words")
        }.exceptionOrNull() ?: error("Expected the backup reader to reject unrelated JSON")

        assertEquals(
            "This file isn't a WordForge backup. Choose a backup exported from WordForge.",
            importErrorMessage(error),
        )
    }

    @Test
    fun malformedBackupsDoNotExposeReaderDetails() {
        listOf("not json", "[]", """{"words": {}}""", """{"words": [42]}""").forEach { json ->
            val error = runCatching {
                JSONObject(json).getJSONArray("words").getJSONObject(0)
            }.exceptionOrNull() ?: error("Expected invalid backup to fail: $json")

            assertEquals(
                "This file isn't a WordForge backup. Choose a backup exported from WordForge.",
                importErrorMessage(error),
            )
        }
    }

    @Test
    fun unreadableFilesExplainHowToRetry() {
        listOf(IOException("provider details"), SecurityException("permission details")).forEach {
            assertEquals(
                "Couldn't read this file. Try choosing it again.",
                importErrorMessage(it),
            )
        }
    }

    @Test
    fun backupValidationStillExplainsTheProblem() {
        assertEquals(
            "Import failed: Item 1 has an empty meaning",
            importErrorMessage(IllegalArgumentException("Item 1 has an empty meaning")),
        )
    }

    @Test
    fun unexpectedFailuresDoNotExposeTechnicalDetails() {
        assertEquals(
            "Couldn't import this backup. Please try again.",
            importErrorMessage(IllegalStateException("database details")),
        )
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotShownAsAnImportFailure() {
        importErrorMessage(CancellationException("Screen closed"))
    }
}
