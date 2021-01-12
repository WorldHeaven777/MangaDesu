package eu.kanade.tachiyomi.data.backup

import android.app.Application
import android.content.Context
import android.os.Build
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.CustomRobolectricGradleTestRunner
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.DHistory
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

/**
 * Test class for the [BackupManager].
 * Note that this does not include the backup create/restore services.
 */
@Config(constants = BuildConfig::class, sdk = intArrayOf(Build.VERSION_CODES.LOLLIPOP))
@RunWith(CustomRobolectricGradleTestRunner::class)
class BackupTest {
    // Create root object
    var root = JsonObject()

    // Create information object
    var information = JsonObject()

    // Create manga array
    var mangaEntries = JsonArray()

    // Create category array
    var categoryEntries = JsonArray()

    lateinit var app: Application
    lateinit var context: Context
    lateinit var source: HttpSource

    lateinit var backupManager: BackupManager

    lateinit var db: DatabaseHelper

    @Before
    fun setup() {
        app = RuntimeEnvironment.application
        context = app.applicationContext
        backupManager = BackupManager(context)
        db = backupManager.databaseHelper

        // Mock the source manager
        val module = object : InjektModule {
            override fun InjektRegistrar.registerInjectables() {
                addSingleton(Mockito.mock(SourceManager::class.java, RETURNS_DEEP_STUBS))
            }
        }
        Injekt.importModule(module)

        source = mock(HttpSource::class.java)
        `when`(backupManager.sourceManager.get(anyLong())).thenReturn(source)

        root.add(Backup.MANGAS, mangaEntries)
        root.add(Backup.CATEGORIES, categoryEntries)
    }

    /**
     * Test that checks if no crashes when no categories in library.
     */
    @Test
    fun testRestoreEmptyCategory() {
        // Initialize json with version 2
        initializeJsonTest(2)

        // Create backup of empty database
        backupManager.backupCategories(categoryEntries)

        // Restore Json
        backupManager.restoreCategories(categoryEntries)

        // Check if empty
        val dbCats = db.getCategories().executeAsBlocking()
        assertThat(dbCats).isEmpty()
    }

    /**
     * Test to check if single category gets restored
     */
    @Test
    fun testRestoreSingleCategory() {
        // Initialize json with version 2
        initializeJsonTest(2)

        // Create category and add to json
        val category = addSingleCategory("category")

        // Restore Json
        backupManager.restoreCategories(categoryEntries)

        // Check if successful
        val dbCats = backupManager.databaseHelper.getCategories().executeAsBlocking()
        assertThat(dbCats).hasSize(1)
        assertThat(dbCats[0].name).isEqualTo(category.name)
    }

    /**
     * Test to check if multiple categories get restored.
     */
    @Test
    fun testRestoreMultipleCategories() {
        // Initialize json with version 2
        initializeJsonTest(2)

        // Create category and add to json
        val category = addSingleCategory("category")
        val category2 = addSingleCategory("category2")
        val category3 = addSingleCategory("category3")
        val category4 = addSingleCategory("category4")
        val category5 = addSingleCategory("category5")

        // Insert category to test if no duplicates on restore.
        db.insertCategory(category).executeAsBlocking()

        // Restore Json
        backupManager.restoreCategories(categoryEntries)

        // Check if successful
        val dbCats = backupManager.databaseHelper.getCategories().executeAsBlocking()
        assertThat(dbCats).hasSize(5)
        assertThat(dbCats[0].name).isEqualTo(category.name)
        assertThat(dbCats[1].name).isEqualTo(category2.name)
        assertThat(dbCats[2].name).isEqualTo(category3.name)
        assertThat(dbCats[3].name).isEqualTo(category4.name)
        assertThat(dbCats[4].name).isEqualTo(category5.name)
    }

    /**
     * Test if restore of manga is successful
     */
    @Test
    fun testRestoreManga() {
        // Initialize json with version 2
        initializeJsonTest(2)

        // Add manga to database
        val manga = getSingleManga("One Piece")
        manga.viewer = 3
        manga.id = db.insertManga(manga).executeAsBlocking().insertedId()

        var favoriteManga = backupManager.databaseHelper.getFavoriteMangas().executeAsBlocking()
        assertThat(favoriteManga).hasSize(1)
        assertThat(favoriteManga[0].viewer).isEqualTo(3)

        // Update json with all options enabled
        mangaEntries.add(backupManager.backupMangaObject(manga, 1))

        // Change manga in database to default values
        val dbManga = getSingleManga("One Piece")
        dbManga.id = manga.id
        db.insertManga(dbManga).executeAsBlocking()

        favoriteManga = backupManager.databaseHelper.getFavoriteMangas().executeAsBlocking()
        assertThat(favoriteManga).hasSize(1)
        assertThat(favoriteManga[0].viewer).isEqualTo(0)

        // Restore local manga
        backupManager.restoreMangaNoFetch(manga, dbManga)

        // Test if restore successful
        favoriteManga = backupManager.databaseHelper.getFavoriteMangas().executeAsBlocking()
        assertThat(favoriteManga).hasSize(1)
        assertThat(favoriteManga[0].viewer).isEqualTo(3)

        // Clear database to test manga fetch
        clearDatabase()

        // Test if successful
        favoriteManga = backupManager.databaseHelper.getFavoriteMangas().executeAsBlocking()
        assertThat(favoriteManga).hasSize(0)

        // Restore Json
        // Create JSON from manga to test parser
        val json = backupManager.parser.toJsonTree(manga)
        // Restore JSON from manga to test parser
        val jsonManga = backupManager.parser.fromJson<MangaImpl>(json)

        // Restore manga with fetch observable
        val networkManga = getSingleManga("One Piece")
        networkManga.description = "This is a description"
        `when`(source.fetchMangaDetails(jsonManga)).thenReturn(Observable.just(networkManga))

        GlobalScope.launch {
            try {
                backupManager.restoreMangaFetch(source, jsonManga)
            } catch (e: Exception) {
                fail("Unexpected onError events")
            }
        }

        // Check if restore successful
        val dbCats = backupManager.databaseHelper.getFavoriteMangas().executeAsBlocking()
        assertThat(dbCats).hasSize(1)
        assertThat(dbCats[0].viewer).isEqualTo(3)
        assertThat(dbCats[0].description).isEqualTo("This is a description")
    }

    /**
     * Test if chapter restore is successful
     */
    @Test
    fun testRestoreChapters() {
        // Initialize json with version 2
        initializeJsonTest(2)

        // Insert manga
        val manga = getSingleManga("One Piece")
        manga.id = backupManager.databaseHelper.insertManga(manga).executeAsBlocking().insertedId()

        // Create restore list
        val chapters = ArrayList<Chapter>()
        for (i in 1..8) {
            val chapter = getSingleChapter("Chapter $i")
            chapter.read = true
            chapters.add(chapter)
        }

        // Check parser
        val chaptersJson = backupManager.parser.toJsonTree(chapters)
        val restoredChapters = backupManager.parser.fromJson<List<ChapterImpl>>(chaptersJson)

        // Fetch chapters from upstream
        // Create list
        val chaptersRemote = ArrayList<Chapter>()
        (1..10).mapTo(chaptersRemote) { getSingleChapter("Chapter $it") }
        `when`(source.fetchChapterList(manga)).thenReturn(Observable.just(chaptersRemote))

        // Call restoreChapterFetchObservable
        GlobalScope.launch {
            try {
                backupManager.restoreChapterFetch(source, manga, restoredChapters)
            } catch (e: Exception) {
                fail("Unexpected onError events")
            }
        }

        val dbCats = backupManager.databaseHelper.getChapters(manga).executeAsBlocking()
        assertThat(dbCats).hasSize(10)
        assertThat(dbCats[0].read).isEqualTo(true)
    }

    /**
     * Test to check if history restore works
     */
    @Test
    fun restoreHistoryForManga() {
        // Initialize json with version 2
        initializeJsonTest(2)

        val manga = getSingleManga("One Piece")
        manga.id = backupManager.databaseHelper.insertManga(manga).executeAsBlocking().insertedId()

        // Create chapter
        val chapter = getSingleChapter("Chapter 1")
        chapter.manga_id = manga.id
        chapter.read = true
        chapter.id = backupManager.databaseHelper.insertChapter(chapter).executeAsBlocking().insertedId()

        val historyJson = getSingleHistory(chapter)

        val historyList = ArrayList<DHistory>()
        historyList.add(historyJson)

        // Check parser
        val historyListJson = backupManager.parser.toJsonTree(historyList)
        val history = backupManager.parser.fromJson<List<DHistory>>(historyListJson)

        // Restore categories
        backupManager.restoreHistoryForManga(history)

        val historyDB = backupManager.databaseHelper.getHistoryByMangaId(manga.id!!).executeAsBlocking()
        assertThat(historyDB).hasSize(1)
        assertThat(historyDB[0].last_read).isEqualTo(1000)
    }

    /**
     * Test to check if tracking restore works
     */
    @Test
    fun restoreTrackForManga() {
        // Initialize json with version 2
        initializeJsonTest(2)

        // Create mangas
        val manga = getSingleManga("One Piece")
        val manga2 = getSingleManga("Bleach")
        manga.id = backupManager.databaseHelper.insertManga(manga).executeAsBlocking().insertedId()
        manga2.id = backupManager.databaseHelper.insertManga(manga2).executeAsBlocking().insertedId()

        // Create track and add it to database
        // This tests duplicate errors.
        val track = getSingleTrack(manga)
        track.last_chapter_read = 5
        backupManager.databaseHelper.insertTrack(track).executeAsBlocking()
        var trackDB = backupManager.databaseHelper.getTracks(manga).executeAsBlocking()
        assertThat(trackDB).hasSize(1)
        assertThat(trackDB[0].last_chapter_read).isEqualTo(5)
        track.last_chapter_read = 7

        // Create track for different manga to test track not in database
        val track2 = getSingleTrack(manga2)
        track2.last_chapter_read = 10

        // Check parser and restore already in database
        var trackList = listOf(track)
        // Check parser
        var trackListJson = backupManager.parser.toJsonTree(trackList)
        var trackListRestore = backupManager.parser.fromJson<List<TrackImpl>>(trackListJson)
        backupManager.restoreTrackForManga(manga, trackListRestore)

        // Assert if restore works.
        trackDB = backupManager.databaseHelper.getTracks(manga).executeAsBlocking()
        assertThat(trackDB).hasSize(1)
        assertThat(trackDB[0].last_chapter_read).isEqualTo(7)

        // Check parser and restore already in database with lower chapter_read
        track.last_chapter_read = 5
        trackList = listOf(track)
        backupManager.restoreTrackForManga(manga, trackList)

        // Assert if restore works.
        trackDB = backupManager.databaseHelper.getTracks(manga).executeAsBlocking()
        assertThat(trackDB).hasSize(1)
        assertThat(trackDB[0].last_chapter_read).isEqualTo(7)

        // Check parser and restore, track not in database
        trackList = listOf(track2)

        // Check parser
        trackListJson = backupManager.parser.toJsonTree(trackList)
        trackListRestore = backupManager.parser.fromJson<List<TrackImpl>>(trackListJson)
        backupManager.restoreTrackForManga(manga2, trackListRestore)

        // Assert if restore works.
        trackDB = backupManager.databaseHelper.getTracks(manga2).executeAsBlocking()
        assertThat(trackDB).hasSize(1)
        assertThat(trackDB[0].last_chapter_read).isEqualTo(10)
    }

    fun clearJson() {
        root = JsonObject()
        information = JsonObject()
        mangaEntries = JsonArray()
        categoryEntries = JsonArray()
    }

    fun initializeJsonTest(version: Int) {
        clearJson()
        backupManager.setVersion(version)
    }

    fun addSingleCategory(name: String): Category {
        val category = Category.create(name)
        val catJson = backupManager.parser.toJsonTree(category)
        categoryEntries.add(catJson)
        return category
    }

    fun clearDatabase() {
        db.deleteMangas().executeAsBlocking()
        db.deleteHistory().executeAsBlocking()
    }

    fun getSingleHistory(chapter: Chapter): DHistory {
        return DHistory(chapter.url, 1000)
    }

    private fun getSingleTrack(manga: Manga): TrackImpl {
        val track = TrackImpl()
        track.title = manga.title
        track.manga_id = manga.id!!
        track.sync_id = 1
        return track
    }

    private fun getSingleManga(title: String): MangaImpl {
        val manga = MangaImpl()
        manga.source = 1
        manga.title = title
        manga.url = "/manga/$title"
        manga.favorite = true
        return manga
    }

    private fun getSingleChapter(name: String): ChapterImpl {
        val chapter = ChapterImpl()
        chapter.name = name
        chapter.url = "/read-online/$name-page-1.html"
        return chapter
    }
}
