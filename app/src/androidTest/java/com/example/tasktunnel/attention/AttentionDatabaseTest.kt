package com.example.tasktunnel.attention

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttentionDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "attention-test.db"

    @Before
    fun cleanBefore() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun eventsPersistAcrossReopenAndCanBeCleared() = runBlocking {
        var database = openDatabase()
        database.attentionEventDao().insert(
            AttentionEventEntity(
                timestampMillis = 100,
                type = AttentionEventType.INTENT.name,
                subtype = AttentionSubtype.PURPOSE_SELECTED.name,
                app = AttentionApp.INSTAGRAM.name,
                surface = null,
                task = null,
                tunnelId = "tunnel-1",
                driftEpisodeId = null,
                decision = null,
                relatedApps = null,
            ),
        )
        database.close()

        database = openDatabase()
        assertEquals("tunnel-1", database.attentionEventDao().getAll().single().tunnelId)
        database.attentionEventDao().clear()
        assertTrue(database.attentionEventDao().getAll().isEmpty())
        database.close()
    }

    private fun openDatabase() = Room.databaseBuilder(
        context,
        AttentionDatabase::class.java,
        databaseName,
    ).build()
}
