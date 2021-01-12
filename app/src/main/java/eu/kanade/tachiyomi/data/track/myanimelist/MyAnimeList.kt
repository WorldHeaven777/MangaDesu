package eu.kanade.tachiyomi.data.track.myanimelist

import android.content.Context
import android.graphics.Color
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

class MyAnimeList(private val context: Context, id: Int) : TrackService(id) {

    private val interceptor by lazy { MyAnimeListInterceptor(this) }
    private val api by lazy { MyAnimeListApi(client, interceptor) }

    override val name = "MyAnimeList"

    override fun getLogo() = R.drawable.ic_tracker_mal

    override fun getLogoColor() = Color.rgb(46, 81, 162)

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            COMPLETED -> getString(R.string.completed)
            ON_HOLD -> getString(R.string.on_hold)
            DROPPED -> getString(R.string.dropped)
            PLAN_TO_READ -> getString(R.string.plan_to_read)
            else -> ""
        }
    }

    override fun getGlobalStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            PLAN_TO_READ -> getString(R.string.plan_to_read)
            COMPLETED -> getString(R.string.completed)
            ON_HOLD -> getString(R.string.on_hold)
            DROPPED -> getString(R.string.dropped)
            else -> ""
        }
    }

    override fun getStatusList(): List<Int> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    }

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun getScoreList(): List<String> {
        return IntRange(0, 10).map(Int::toString)
    }

    override fun displayScore(track: Track): String {
        return track.score.toInt().toString()
    }

    override suspend fun update(track: Track): Track {
        if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
            track.status = COMPLETED
        }

        return api.updateLibManga(track)
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track)
        if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.score = DEFAULT_SCORE.toFloat()
            track.status = DEFAULT_STATUS
            return api.addLibManga(track)
        }
        return track
    }

    override fun canRemoveFromService(): Boolean = true

    override suspend fun removeFromService(track: Track): Boolean {
        return api.remove(track)
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    suspend fun login(csrfToken: String) = login("myanimelist", csrfToken)

    override suspend fun login(username: String, password: String): Boolean {
        return try {
            saveCSRF(password)
            saveCredentials(username, password)
            true
        } catch (e: Exception) {
            Timber.e(e)
            logout()
            false
        }
    }

    // Attempt to login again if cookies have been cleared but credentials are still filled
    suspend fun ensureLoggedIn() {
        if (isAuthorized) return
        if (!isLogged) throw Exception("MAL Login Credentials not found")
    }

    override fun logout() {
        super.logout()
        preferences.trackToken(this).delete()
        networkService.cookieManager.remove(BASE_URL.toHttpUrlOrNull()!!)
    }

    private val isAuthorized: Boolean
        get() = super.isLogged && getCSRF().isNotEmpty() && checkCookies()

    fun getCSRF(): String = preferences.trackToken(this).getOrDefault()

    private fun saveCSRF(csrf: String) = preferences.trackToken(this).set(csrf)

    private fun checkCookies(): Boolean {
        var ckCount = 0
        val url = BASE_URL.toHttpUrlOrNull()!!
        for (ck in networkService.cookieManager.get(url)) {
            if (ck.name == USER_SESSION_COOKIE || ck.name == LOGGED_IN_COOKIE) ckCount++
        }

        return ckCount == 2
    }

    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 6

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0

        const val BASE_URL = "https://myanimelist.net"
        const val USER_SESSION_COOKIE = "MALSESSIONID"
        const val LOGGED_IN_COOKIE = "is_logged_in"
    }
}
