package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

data class OAuth(
    val access_token: String,
    val token_type: String,
    val expires: Long,
    val expires_in: Long
) {

    fun isExpired() = System.currentTimeMillis() > expires
}

data class ALManga(
    val media_id: Int,
    val title_romaji: String,
    val image_url_lge: String,
    val description: String?,
    val type: String,
    val publishing_status: String,
    val start_date_fuzzy: Long,
    val total_chapters: Int
) {

    fun toTrack() = TrackSearch.create(TrackManager.ANILIST).apply {
        media_id = this@ALManga.media_id
        title = title_romaji
        total_chapters = this@ALManga.total_chapters
        cover_url = image_url_lge
        summary = description ?: ""
        tracking_url = AnilistApi.mangaUrl(media_id)
        publishing_status = this@ALManga.publishing_status
        publishing_type = type
        if (start_date_fuzzy != 0L) {
            start_date = try {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                outputDf.format(start_date_fuzzy)
            } catch (e: Exception) {
                ""
            }
        }
    }
}

data class ALUserManga(
    val library_id: Long,
    val list_status: String,
    val score_raw: Int,
    val chapters_read: Int,
    val manga: ALManga
) {

    fun toTrack() = Track.create(TrackManager.ANILIST).apply {
        media_id = manga.media_id
        status = toTrackStatus()
        score = score_raw.toFloat()
        last_chapter_read = chapters_read
        library_id = this@ALUserManga.library_id
        total_chapters = manga.total_chapters
    }

    private fun toTrackStatus() = when (list_status) {
        "CURRENT" -> Anilist.READING
        "COMPLETED" -> Anilist.COMPLETED
        "PAUSED" -> Anilist.PAUSED
        "DROPPED" -> Anilist.DROPPED
        "PLANNING" -> Anilist.PLANNING
        "REPEATING" -> Anilist.REPEATING
        else -> Anilist.READING
    }
}

fun Track.toAnilistStatus() = when (status) {
    Anilist.READING -> "CURRENT"
    Anilist.COMPLETED -> "COMPLETED"
    Anilist.PAUSED -> "PAUSED"
    Anilist.DROPPED -> "DROPPED"
    Anilist.PLANNING -> "PLANNING"
    Anilist.REPEATING -> "REPEATING"
    else -> "CURRENT"
}

private val preferences: PreferencesHelper by injectLazy()

fun Track.toAnilistScore(): String = when (preferences.anilistScoreType().getOrDefault()) {
// 10 point
    "POINT_10" -> (score.toInt() / 10).toString()
// 100 point
    "POINT_100" -> score.toInt().toString()
// 5 stars
    "POINT_5" -> when {
        score == 0f -> "0"
        score < 30 -> "1"
        score < 50 -> "2"
        score < 70 -> "3"
        score < 90 -> "4"
        else -> "5"
    }
// Smiley
    "POINT_3" -> when {
        score == 0f -> "0"
        score <= 35 -> ":("
        score <= 60 -> ":|"
        else -> ":)"
    }
// 10 point decimal
    "POINT_10_DECIMAL" -> (score / 10).toString()
    else -> throw Exception("Unknown score type")
}
