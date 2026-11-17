package com.wordforge.ui.screens

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.json.JSONException

internal fun importErrorMessage(error: Throwable): String = when (error) {
    is CancellationException -> throw error
    is JSONException ->
        "This file isn't a WordForge backup. Choose a backup exported from WordForge."
    is IOException, is SecurityException ->
        "Couldn't read this file. Try choosing it again."
    is IllegalArgumentException -> "Import failed: ${error.message ?: "Invalid backup"}"
    else -> "Couldn't import this backup. Please try again."
}
