package eu.kanade.tachiyomi.data.download.model

import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadStore
import eu.kanade.tachiyomi.source.model.Page
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.CopyOnWriteArrayList

class DownloadQueue(
    private val store: DownloadStore,
    private val queue: MutableList<Download> = CopyOnWriteArrayList<Download>()
) :
    List<Download> by queue {

    private val statusSubject = PublishSubject.create<Download>()

    private val updatedRelay = PublishRelay.create<Unit>()

    private val downloadListeners = mutableListOf<DownloadListener>()

    fun addAll(downloads: List<Download>) {
        downloads.forEach { download ->
            download.setStatusSubject(statusSubject)
            download.setStatusCallback(::setPagesFor)
            download.status = Download.QUEUE
        }
        queue.addAll(downloads)
        store.addAll(downloads)
        updatedRelay.call(Unit)
    }

    fun remove(download: Download) {
        val removed = queue.remove(download)
        store.remove(download)
        download.setStatusSubject(null)
        download.setStatusCallback(null)
        if (download.status == Download.DOWNLOADING || download.status == Download.QUEUE)
            download.status = Download.NOT_DOWNLOADED
        downloadListeners.forEach { it.updateDownload(download) }
        if (removed) {
            updatedRelay.call(Unit)
        }
    }

    fun updateListeners() {
        downloadListeners.forEach { it.updateDownloads() }
    }

    fun remove(chapter: Chapter) {
        find { it.chapter.id == chapter.id }?.let { remove(it) }
    }

    fun remove(chapters: List<Chapter>) {
        for (chapter in chapters) { remove(chapter) }
    }

    fun remove(manga: Manga) {
        filter { it.manga.id == manga.id }.forEach { remove(it) }
    }

    fun clear() {
        queue.forEach { download ->
            download.setStatusSubject(null)
            download.setStatusCallback(null)
            if (download.status == Download.DOWNLOADING || download.status == Download.QUEUE)
                download.status = Download.NOT_DOWNLOADED
            downloadListeners.forEach { it.updateDownload(download) }
        }
        queue.clear()
        store.clear()
        updatedRelay.call(Unit)
    }

    fun getActiveDownloads(): Observable<Download> =
        Observable.from(this).filter { download -> download.status == Download.DOWNLOADING }

    fun getStatusObservable(): Observable<Download> = statusSubject.onBackpressureBuffer()

    fun getUpdatedObservable(): Observable<List<Download>> = updatedRelay.onBackpressureBuffer()
        .startWith(Unit)
        .map { this }

    private fun setPagesFor(download: Download) {
        if (download.status == Download.DOWNLOADING) {
            if (download.pages != null)
                for (page in download.pages!!)
                    page.setStatusCallback {
                        callListeners(download)
                    }
            downloadListeners.forEach { it.updateDownload(download) }
        } else if (download.status == Download.DOWNLOADED || download.status == Download.ERROR) {
            setPagesSubject(download.pages, null)
            downloadListeners.forEach { it.updateDownload(download) }
        } else {
            downloadListeners.forEach { it.updateDownload(download) }
        }
    }

    private fun callListeners(download: Download) {
        downloadListeners.forEach { it.updateDownload(download) }
    }

    fun getProgressObservable(): Observable<Download> {
        return statusSubject.onBackpressureBuffer()
            .startWith(getActiveDownloads())
            .flatMap { download ->
                if (download.status == Download.DOWNLOADING) {
                    val pageStatusSubject = PublishSubject.create<Int>()
                    setPagesSubject(download.pages, pageStatusSubject)
                    downloadListeners.forEach { it.updateDownload(download) }
                    return@flatMap pageStatusSubject
                        .onBackpressureBuffer()
                        .filter { it == Page.READY }
                        .map { download }
                } else if (download.status == Download.DOWNLOADED || download.status == Download.ERROR) {
                    setPagesSubject(download.pages, null)
                    downloadListeners.forEach { it.updateDownload(download) }
                }
                Observable.just(download)
            }
            .filter { it.status == Download.DOWNLOADING }
    }

    private fun setPagesSubject(pages: List<Page>?, subject: PublishSubject<Int>?) {
        if (pages != null) {
            for (page in pages) {
                page.setStatusSubject(subject)
            }
        }
    }

    fun addListener(listener: DownloadListener) {
        downloadListeners.add(listener)
    }

    fun removeListener(listener: DownloadListener) {
        downloadListeners.remove(listener)
    }

    interface DownloadListener {
        fun updateDownload(download: Download)
        fun updateDownloads()
    }
}
