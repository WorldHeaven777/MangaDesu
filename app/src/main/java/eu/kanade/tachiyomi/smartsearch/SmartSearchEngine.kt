package eu.kanade.tachiyomi.smartsearch

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.await
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.supervisorScope
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import kotlin.coroutines.CoroutineContext

class SmartSearchEngine(
    parentContext: CoroutineContext,
    val extraSearchParams: String? = null
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = parentContext + Job() + Dispatchers.Default

    private val db: DatabaseHelper by injectLazy()

    private val normalizedLevenshtein = NormalizedLevenshtein()

    /*suspend fun smartSearch(source: CatalogueSource, title: String): SManga? {
        val cleanedTitle = cleanSmartSearchTitle(title)

        val queries = getSmartSearchQueries(cleanedTitle)

        val eligibleManga = supervisorScope {
            queries.map { query ->
                async(Dispatchers.Default) {
                    val builtQuery = if(extraSearchParams != null) {
                        "$query ${extraSearchParams.trim()}"
                    } else query

                    val searchResults = source.fetchSearchManga(1, builtQuery, FilterList())
                        .toSingle().await(Schedulers.io())

                    searchResults.mangas.map {
                        val cleanedMangaTitle = cleanSmartSearchTitle(it.title)
                        val normalizedDistance = normalizedLevenshtein.similarity(cleanedTitle, cleanedMangaTitle)
                        SearchEntry(it, normalizedDistance)
                    }.filter { (_, normalizedDistance) ->
                        normalizedDistance >= MIN_SMART_ELIGIBLE_THRESHOLD
                    }
                }
            }.flatMap { it.await() }
        }

        return eligibleManga.maxBy { it.dist }?.manga
    }*/

    suspend fun normalSearch(source: CatalogueSource, title: String): SManga? {
        val eligibleManga = supervisorScope {
            val searchQuery = if (extraSearchParams != null) {
                "$title ${extraSearchParams.trim()}"
            } else title
            val searchResults = source.fetchSearchManga(1, searchQuery, FilterList()).toSingle().await(Schedulers.io())

            if (searchResults.mangas.size == 1)
                return@supervisorScope listOf(SearchEntry(searchResults.mangas.first(), 0.0))

            searchResults.mangas.map {
                val normalizedDistance = normalizedLevenshtein.similarity(title, it.title)
                SearchEntry(it, normalizedDistance)
            }.filter { (_, normalizedDistance) ->
                normalizedDistance >= MIN_NORMAL_ELIGIBLE_THRESHOLD
            }
        }

        return eligibleManga.maxBy { it.dist }?.manga
    }
    private fun removeTextInBrackets(text: String, readForward: Boolean): String {
        val bracketPairs = listOf(
            '(' to ')',
            '[' to ']',
            '<' to '>',
            '{' to '}'
        )
        var openingBracketPairs = bracketPairs.mapIndexed { index, (opening, _) ->
            opening to index
        }.toMap()
        var closingBracketPairs = bracketPairs.mapIndexed { index, (_, closing) ->
            closing to index
        }.toMap()

        // Reverse pairs if reading backwards
        if (!readForward) {
            val tmp = openingBracketPairs
            openingBracketPairs = closingBracketPairs
            closingBracketPairs = tmp
        }

        val depthPairs = bracketPairs.map { 0 }.toMutableList()

        val result = StringBuilder()
        for (c in if (readForward) text else text.reversed()) {
            val openingBracketDepthIndex = openingBracketPairs[c]
            if (openingBracketDepthIndex != null) {
                depthPairs[openingBracketDepthIndex]++
            } else {
                val closingBracketDepthIndex = closingBracketPairs[c]
                if (closingBracketDepthIndex != null) {
                    depthPairs[closingBracketDepthIndex]--
                } else {
                    if (depthPairs.all { it <= 0 }) {
                        result.append(c)
                    } else {
                        // In brackets, do not append to result
                    }
                }
            }
        }

        return result.toString()
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    suspend fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        }
        return localManga
    }

    companion object {
        const val MIN_SMART_ELIGIBLE_THRESHOLD = 0.4
        const val MIN_NORMAL_ELIGIBLE_THRESHOLD = 0.4

        private val titleRegex = Regex("[^a-zA-Z0-9- ]")
        private val consecutiveSpacesRegex = Regex(" +")
    }
}

data class SearchEntry(val manga: SManga, val dist: Double)
