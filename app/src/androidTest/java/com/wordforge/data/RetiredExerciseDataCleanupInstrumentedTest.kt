package com.wordforge.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetiredExerciseDataCleanupInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val retiredPreferences = listOf("wordforge_llm_credentials", "wordforge_llm_settings")
    private val retiredAliases = listOf("wordforge_llm_openai", "wordforge_llm_gemini")
    private val unrelatedName = "wordforge_cleanup_test_unrelated"

    @After
    fun cleanTestData() {
        removeRetiredExerciseData(context)
        context.deleteSharedPreferences(unrelatedName)
        keyStore().deleteEntry(unrelatedName)
    }

    @Test
    fun deletesRetiredFilesAndKeysWhilePreservingUnrelatedDataAcrossRepeatedRuns() {
        retiredPreferences.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).also {
                assertTrue(it.edit().putString("saved_value", "test value").commit())
            }
        }
        val unrelatedStore = context.getSharedPreferences(unrelatedName, Context.MODE_PRIVATE)
        assertTrue(unrelatedStore.edit().putBoolean("retained", true).commit())
        (retiredAliases + unrelatedName).forEach(::createKey)
        val store = keyStore()
        retiredAliases.forEach { assertTrue(store.containsAlias(it)) }

        repeat(2) {
            removeRetiredExerciseData(context)

            retiredPreferences.forEach { name ->
                assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
                assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
            }
            retiredAliases.forEach { assertFalse(store.containsAlias(it)) }
            assertTrue(unrelatedStore.getBoolean("retained", false))
            assertTrue(store.containsAlias(unrelatedName))
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(alias: String) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}
