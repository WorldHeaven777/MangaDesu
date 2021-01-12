package eu.kanade.tachiyomi.data.database.queries

import eu.kanade.tachiyomi.data.database.tables.CategoryTable as Category
import eu.kanade.tachiyomi.data.database.tables.ChapterTable as Chapter
import eu.kanade.tachiyomi.data.database.tables.HistoryTable as History
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable as MangaCategory
import eu.kanade.tachiyomi.data.database.tables.MangaTable as Manga

/**
 * Query to get the manga from the library, with their categories and unread count.
 */
val libraryQuery =
    """
    SELECT M.*, COALESCE(MC.${MangaCategory.COL_CATEGORY_ID}, 0) AS ${Manga.COL_CATEGORY}
    FROM (
        SELECT ${Manga.TABLE}.*, COALESCE(C.unread, 0) AS ${Manga.COL_UNREAD}, COALESCE(R.hasread, 0) AS ${Manga.COL_HAS_READ}
        FROM ${Manga.TABLE}
        LEFT JOIN (
            SELECT ${Chapter.COL_MANGA_ID}, COUNT(*) AS unread
            FROM ${Chapter.TABLE}
            WHERE ${Chapter.COL_READ} = 0
            GROUP BY ${Chapter.COL_MANGA_ID}
        ) AS C
        ON ${Manga.COL_ID} = C.${Chapter.COL_MANGA_ID}
        LEFT JOIN (
            SELECT ${Chapter.COL_MANGA_ID}, COUNT(*) AS hasread
            FROM ${Chapter.TABLE}
            WHERE ${Chapter.COL_READ} = 1
            GROUP BY ${Chapter.COL_MANGA_ID}
        ) AS R
        ON ${Manga.COL_ID} = R.${Chapter.COL_MANGA_ID}
        WHERE ${Manga.COL_FAVORITE} = 1
        GROUP BY ${Manga.COL_ID}
        ORDER BY ${Manga.COL_TITLE}
    ) AS M
    LEFT JOIN (
        SELECT * FROM ${MangaCategory.TABLE}) AS MC
        ON MC.${MangaCategory.COL_MANGA_ID} = M.${Manga.COL_ID}
"""

/**
 * Query to get the recent chapters of manga from the library up to a date.
 */
fun getRecentsQuery() =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, * FROM ${Manga.TABLE} JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    WHERE ${Manga.COL_FAVORITE} = 1 
    AND ${Chapter.COL_DATE_UPLOAD} > ?
    AND ${Chapter.COL_DATE_FETCH} > ${Manga.COL_DATE_ADDED}
    ORDER BY ${Chapter.COL_DATE_UPLOAD} DESC
"""

/**
 * Query to get the recently added manga
 */
fun getRecentAdditionsQuery(search: String, endless: Boolean) =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, * FROM ${Manga.TABLE}
    WHERE ${Manga.COL_FAVORITE} = 1
    AND ${Manga.COL_DATE_ADDED} > ?
    AND lower(${Manga.COL_TITLE}) LIKE '%$search%'
    ORDER BY ${Manga.COL_DATE_ADDED} DESC
    ${if (endless) "" else "LIMIT 8"}
"""

/**
 * Query to get the manga with recently uploaded chapters
 */
fun getRecentsQueryDistinct(search: String, endless: Boolean) =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, ${Manga.TABLE}.*, ${Chapter.TABLE}.*
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    JOIN (
    SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID},${Chapter.TABLE}.${Chapter.COL_ID} as ${History.COL_CHAPTER_ID},MAX(${Chapter.TABLE}.${Chapter.COL_DATE_UPLOAD}) 
    FROM ${Chapter.TABLE} JOIN ${Manga.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    WHERE ${Chapter.COL_DATE_UPLOAD} > ?
    AND ${Chapter.COL_READ} = 0
    GROUP BY ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}) AS newest_chapter
    ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = newest_chapter.${Chapter.COL_MANGA_ID}
    WHERE ${Manga.COL_FAVORITE} = 1
    AND newest_chapter.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
    AND ${Chapter.COL_DATE_FETCH} > ${Manga.COL_DATE_ADDED}
    AND lower(${Manga.COL_TITLE}) LIKE '%$search%'
    ORDER BY ${Chapter.COL_DATE_UPLOAD} DESC
    ${if (endless) "" else "LIMIT 8"}
"""

/**
 * Query to get the recently read chapters of manga from the library up to a date.
 * The max_last_read table contains the most recent chapters grouped by manga
 * The select statement returns all information of chapters that have the same id as the chapter in max_last_read
 * and are read after the given time period
 * @return return limit is 25
 */
fun getRecentMangasQuery(offset: Int = 0, search: String = "") =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, ${Manga.TABLE}.*, ${Chapter.TABLE}.*, ${History.TABLE}.*
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    JOIN (
    SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID},${Chapter.TABLE}.${Chapter.COL_ID} as ${History.COL_CHAPTER_ID}, MAX(${History.TABLE}.${History.COL_LAST_READ}) as ${History.COL_LAST_READ}
    FROM ${Chapter.TABLE} JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    GROUP BY ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}) AS max_last_read
    ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = max_last_read.${Chapter.COL_MANGA_ID}
    WHERE ${History.TABLE}.${History.COL_LAST_READ} > ?
    AND max_last_read.${History.COL_CHAPTER_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    AND lower(${Manga.TABLE}.${Manga.COL_TITLE}) LIKE '%$search%'
    ORDER BY max_last_read.${History.COL_LAST_READ} DESC
    LIMIT 25 OFFSET $offset
"""

/**
 * Query to get the recently read chapters of manga from the library up to a date.
 * The max_last_read table contains the most recent chapters grouped by manga
 * The select statement returns all information of chapters that have the same id as the chapter in max_last_read
 * and are read after the given time period
 */
fun getRecentMangasLimitQuery(limit: Int = 25, search: String = "") =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, ${Manga.TABLE}.*, ${Chapter.TABLE}.*, ${History.TABLE}.*
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    JOIN (
    SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID},${Chapter.TABLE}.${Chapter.COL_ID} as ${History.COL_CHAPTER_ID}, MAX(${History.TABLE}.${History.COL_LAST_READ}) as ${History.COL_LAST_READ}
    FROM ${Chapter.TABLE} JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    GROUP BY ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}) AS max_last_read
    ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = max_last_read.${Chapter.COL_MANGA_ID}
    WHERE ${History.TABLE}.${History.COL_LAST_READ} > ?
    AND max_last_read.${History.COL_CHAPTER_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    AND lower(${Manga.TABLE}.${Manga.COL_TITLE}) LIKE '%$search%'
    ORDER BY max_last_read.${History.COL_LAST_READ} DESC
    LIMIT $limit
"""

/**
 * Query to get the recently read manga that has more chapters to read
 * The first from checks that there's an unread chapter
 * The max_last_read table contains the most recent chapters grouped by manga
 * The select statement returns all information of chapters that have the same id as the chapter in max_last_read
 * and are read after the given time period
 */
fun getRecentReadWithUnreadChapters(search: String = "", endless: Boolean) =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, ${Manga.TABLE}.*, ${Chapter.TABLE}.*, ${History.TABLE}.*
    FROM (
        SELECT ${Manga.TABLE}.*
        FROM ${Manga.TABLE}
        LEFT JOIN (
            SELECT ${Chapter.COL_MANGA_ID}, COUNT(*) AS unread
            FROM ${Chapter.TABLE}
            WHERE ${Chapter.COL_READ} = 0
            GROUP BY ${Chapter.COL_MANGA_ID}
        ) AS C
        ON ${Manga.COL_ID} = C.${Chapter.COL_MANGA_ID}
        WHERE C.unread > 0
        GROUP BY ${Manga.COL_ID}
        ORDER BY ${Manga.COL_TITLE}
    ) AS ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    JOIN (
        SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID},${Chapter.TABLE}.${Chapter.COL_ID} as ${History.COL_CHAPTER_ID}, MAX(${History.TABLE}.${History.COL_LAST_READ}) as ${History.COL_LAST_READ}
        FROM ${Chapter.TABLE} JOIN ${History.TABLE}
        ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
        GROUP BY ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}) AS max_last_read
    ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = max_last_read.${Chapter.COL_MANGA_ID}
    WHERE ${History.TABLE}.${History.COL_LAST_READ} > ?
    AND max_last_read.${History.COL_CHAPTER_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    AND lower(${Manga.TABLE}.${Manga.COL_TITLE}) LIKE '%$search%'
    ORDER BY max_last_read.${History.COL_LAST_READ} DESC
    ${if (endless) "" else "LIMIT 8"}
"""

fun getHistoryByMangaId() =
    """
    SELECT ${History.TABLE}.*
    FROM ${History.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
    WHERE ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = ? AND ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
"""

fun getHistoryByChapterUrl() =
    """
    SELECT ${History.TABLE}.*
    FROM ${History.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
    WHERE ${Chapter.TABLE}.${Chapter.COL_URL} = ? AND ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
"""

fun getLastReadMangaQuery() =
    """
    SELECT ${Manga.TABLE}.*, MAX(${History.TABLE}.${History.COL_LAST_READ}) AS max
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    WHERE ${Manga.TABLE}.${Manga.COL_FAVORITE} = 1
    GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
    ORDER BY max DESC
"""

fun getTotalChapterMangaQuery() =
    """
    SELECT ${Manga.TABLE}.*
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
    ORDER by COUNT(*)
"""

/**
 * Query to get the categories for a manga.
 */
fun getCategoriesForMangaQuery() =
    """
    SELECT ${Category.TABLE}.* FROM ${Category.TABLE}
    JOIN ${MangaCategory.TABLE} ON ${Category.TABLE}.${Category.COL_ID} =
    ${MangaCategory.TABLE}.${MangaCategory.COL_CATEGORY_ID}
    WHERE ${MangaCategory.COL_MANGA_ID} = ?
"""
