package eu.kanade.tachiyomi.data.database.resolvers

import android.content.ContentValues
import androidx.annotation.NonNull
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.inTransactionReturn
import eu.kanade.tachiyomi.data.database.mappers.HistoryPutResolver
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.tables.HistoryTable

class HistoryLastReadPutResolver : HistoryPutResolver() {

    /**
     * Updates last_read time of chapter
     */
    override fun performPut(@NonNull db: StorIOSQLite, @NonNull history: History): PutResult = db.inTransactionReturn {
        val updateQuery = mapToUpdateQuery(history)

        val cursor = db.lowLevel().query(
            Query.builder()
                .table(updateQuery.table())
                .where(updateQuery.where())
                .whereArgs(updateQuery.whereArgs())
                .build()
        )

        val putResult: PutResult

        try {
            if (cursor.count == 0) {
                val insertQuery = mapToInsertQuery(history)
                val insertedId = db.lowLevel().insert(insertQuery, mapToContentValues(history))
                putResult = PutResult.newInsertResult(insertedId, insertQuery.table())
            } else {
                val numberOfRowsUpdated = db.lowLevel().update(updateQuery, mapToUpdateContentValues(history))
                putResult = PutResult.newUpdateResult(numberOfRowsUpdated, updateQuery.table())
            }
        } finally {
            cursor.close()
        }

        putResult
    }

    /**
     * Creates update query
     * @param obj history object
     */
    override fun mapToUpdateQuery(obj: History) = UpdateQuery.builder()
        .table(HistoryTable.TABLE)
        .where("${HistoryTable.COL_CHAPTER_ID} = ?")
        .whereArgs(obj.chapter_id)
        .build()

    /**
     * Create content query
     * @param history object
     */
    fun mapToUpdateContentValues(history: History) = ContentValues(1).apply {
        put(HistoryTable.COL_LAST_READ, history.last_read)
    }
}
