package com.maverde.crunchybadges.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.models.AnimeGenerationCatalog
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the published Anime Generation catalog JSON and merges it into the DB.
 * Scheduled periodically by WorkManager; also enqueued once on app launch.
 */
class AnimeGenerationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val url = inputData.getString("url") ?: CATALOG_URL
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.retry()
                resp.body?.string() ?: return@withContext Result.retry()
            }
            val catalog = json.decodeFromString(AnimeGenerationCatalog.serializer(), body)
            val db = AnimeDatabase.getDatabase(applicationContext)
            val repo = AnimeRepository(db.animeDao(), applicationContext)
            repo.ingestAnimeGeneration(catalog)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AGSyncWorker", "sync failed", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "anime_generation_sync"
        // NOTE: replace with the real published URL (GitHub Pages / raw) from the
        // pipeline once it is live. The worker also accepts an inputData["url"]
        // override (used by tests and for pointing at a fixture).
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/maverde73/crunchyroll-android-badges/main/docs/catalog/catalog_anime_generation.json"
    }
}
