package eu.kanade.tachiyomi.ui.migration

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchItem
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchMangaItem
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchPresenter

class SearchPresenter(
    initialQuery: String? = "",
    private val manga: Manga,
    sources: List<CatalogueSource>? = null
) : GlobalSearchPresenter(initialQuery, sourcesToUse = sources) {

    override fun getEnabledSources(): List<CatalogueSource> {
        // Put the source of the selected manga at the top
        return super.getEnabledSources()
            .sortedByDescending { it.id == manga.source }
    }

    override fun createCatalogueSearchItem(source: CatalogueSource, results: List<GlobalSearchMangaItem>?): GlobalSearchItem {
        // Set the catalogue search item as highlighted if the source matches that of the selected manga
        return GlobalSearchItem(source, results, source.id == manga.source)
    }
}
