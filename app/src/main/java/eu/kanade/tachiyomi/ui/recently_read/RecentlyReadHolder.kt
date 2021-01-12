package eu.kanade.tachiyomi.ui.recently_read

import android.view.View
import coil.api.clear
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.image.coil.loadLibraryManga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.lang.toTimestampString
import kotlinx.android.synthetic.main.recently_read_item.*
import java.util.Date

/**
 * Holder that contains recent manga item
 * Uses R.layout.item_recently_read.
 * UI related actions should be called from here.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new recent chapter holder.
 */
class RecentlyReadHolder(
    view: View,
    val adapter: RecentlyReadAdapter
) : BaseFlexibleViewHolder(view, adapter) {

    init {
        remove.setOnClickListener {
            adapter.removeClickListener.onRemoveClick(adapterPosition)
        }

        resume.setOnClickListener {
            adapter.resumeClickListener.onResumeClick(adapterPosition)
        }

        cover.setOnClickListener {
            adapter.coverClickListener.onCoverClick(adapterPosition)
        }
    }

    /**
     * Set values of view
     *
     * @param item item containing history information
     */
    fun bind(item: MangaChapterHistory) {
        // Retrieve objects
        val (manga, chapter, history) = item

        // Set manga title
        title.text = manga.title

        // Set source + chapter title
        val formattedNumber = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
        manga_source.text = itemView.context.getString(R.string.source_dash_chapter_)
            .format(adapter.sourceManager.getOrStub(manga.source).toString(), formattedNumber)

        // Set last read timestamp title
        last_read.text = Date(history.last_read).toTimestampString(adapter.dateFormat)

        // Set cover
        cover.clear()
        cover.loadLibraryManga(manga)
    }
}
