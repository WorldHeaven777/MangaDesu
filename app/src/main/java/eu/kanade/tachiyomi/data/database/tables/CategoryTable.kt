package eu.kanade.tachiyomi.data.database.tables

object CategoryTable {

    const val TABLE = "categories"

    const val COL_ID = "_id"

    const val COL_NAME = "name"

    const val COL_ORDER = "sort"

    const val COL_FLAGS = "flags"

    const val COL_MANGA_ORDER = "manga_order"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_NAME TEXT NOT NULL,
            $COL_ORDER INTEGER NOT NULL,
            $COL_FLAGS INTEGER NOT NULL,
            $COL_MANGA_ORDER TEXT NOT NULL
            )"""

    val addMangaOrder: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_MANGA_ORDER TEXT"
}
