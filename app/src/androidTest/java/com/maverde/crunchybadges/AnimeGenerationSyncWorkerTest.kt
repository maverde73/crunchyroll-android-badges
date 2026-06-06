package com.maverde.crunchybadges

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.sync.AnimeGenerationSyncWorker
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeGenerationSyncWorkerTest {

    @Test fun workerDownloadsAndIngests() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """{"version":1,"generated_at":"now","titles":[
                {"ag_id":"a1","title":"Lamù","matched_crunchyroll_id":null,
                 "audio_locales":["it-IT"],"subtitle_locales":["it-IT"],
                 "deep_link_url":"https://pv/a1"}]}"""
        ))
        server.start()

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<AnimeGenerationSyncWorker>(ctx)
            .setInputData(androidx.work.workDataOf("url" to server.url("/c.json").toString()))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val db = AnimeDatabase.getDatabase(ctx)
        assertTrue(db.animeDao().getSeriesById("ag:a1")!!.isOnAnimeGeneration())
        server.shutdown()
    }
}
