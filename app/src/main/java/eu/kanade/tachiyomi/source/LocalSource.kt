package eu.kanade.tachiyomi.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.EpubFile
import eu.kanade.tachiyomi.util.system.ImageUtil
import junrar.Archive
import junrar.rarfile.FileHeader
import rx.Observable
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.Scanner
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class LocalSource(private val context: Context) : CatalogueSource {
    companion object {
        const val ID = 0L
        const val HELP_URL = "https://tachiyomi.org/help/guides/reading-local-manga/"

        private const val COVER_NAME = "cover.jpg"
        private val SUPPORTED_ARCHIVE_TYPES = setOf("zip", "rar", "cbr", "cbz", "epub")

        private val POPULAR_FILTERS = FilterList(OrderBy())
        private val LATEST_FILTERS =
            FilterList(OrderBy().apply { state = Filter.Sort.Selection(1, false) })
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)

        fun updateCover(context: Context, manga: SManga, input: InputStream): File? {
            val dir = getBaseDirectories(context).asSequence().firstOrNull()
            if (dir == null) {
                input.close()
                return null
            }
            val cover = File("${dir.absolutePath}/${manga.url}", COVER_NAME)
            if (cover.exists()) cover.delete()

            cover.parentFile?.mkdirs()
            input.use {
                cover.outputStream().use {
                    input.copyTo(it)
                }
            }
            return cover
        }

        private fun getBaseDirectories(context: Context): List<File> {
            val c = context.getString(R.string.app_name) + File.separator + "local"
            val oldLibrary = "Tachiyomi" + File.separator + "local"
            return DiskUtil.getExternalStorages(context).map {
                listOf(File(it.absolutePath, c), File(it.absolutePath, oldLibrary))
            }.flatten()
        }
    }

    override val id = ID
    override val name = context.getString(R.string.local_source)
    override val lang = ""
    override val supportsLatest = true

    override fun toString() = context.getString(R.string.local_source)

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", POPULAR_FILTERS)

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList
    ): Observable<MangasPage> {
        val baseDirs = getBaseDirectories(context)

        val time =
            if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L
        var mangaDirs = baseDirs
            .asSequence()
            .mapNotNull { it.listFiles()?.toList() }.flatten()
            .filter { it.isDirectory }
            .filter { if (time == 0L) it.name.contains(query, ignoreCase = true) else it.lastModified() >= time }
            .distinctBy { it.name }

        val state = ((if (filters.isEmpty()) POPULAR_FILTERS else filters)[0] as OrderBy).state
        when (state?.index) {
            0 -> {
                if (state.ascending) mangaDirs =
                    mangaDirs.sortedBy { it.name.toLowerCase(Locale.ENGLISH) }
                else mangaDirs =
                    mangaDirs.sortedByDescending { it.name.toLowerCase(Locale.ENGLISH) }
            }
            1 -> {
                if (state.ascending) mangaDirs = mangaDirs.sortedBy(File::lastModified)
                else mangaDirs = mangaDirs.sortedByDescending(File::lastModified)
            }
        }

        val mangas = mangaDirs.map { mangaDir ->
            SManga.create().apply {
                title = mangaDir.name
                url = mangaDir.name

                // Try to find the cover
                for (dir in baseDirs) {
                    val cover = File("${dir.absolutePath}/$url", COVER_NAME)
                    if (cover.exists()) {
                        thumbnail_url = cover.absolutePath
                        break
                    }
                }

                // Copy the cover from the first chapter found.
                if (thumbnail_url == null) {
                    val chapters = fetchChapterList(this).toBlocking().first()
                    if (chapters.isNotEmpty()) {
                        try {
                            val dest = updateCover(chapters.last(), this)
                            thumbnail_url = dest?.absolutePath
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }
                }
            }
        }
        return Observable.just(MangasPage(mangas.toList(), false))
    }

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", LATEST_FILTERS)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val baseDirs = getBaseDirectories(context)
        baseDirs
            .mapNotNull { File(it, manga.url).listFiles()?.toList() }
            .flatten()
            .filter { it.extension == "json" }.firstOrNull()?.apply {
                val json = Gson().fromJson(
                    Scanner(this).useDelimiter("\\Z").next(),
                    JsonObject::class.java
                )
                manga.title = json["title"]?.asString ?: manga.title
                manga.author = json["author"]?.asString ?: manga.author
                manga.artist = json["artist"]?.asString ?: manga.artist
                manga.description = json["description"]?.asString ?: manga.description
                manga.genre = json["genre"]?.asJsonArray?.map { it.asString }?.joinToString(", ")
                    ?: manga.genre
                manga.status = json["status"]?.asInt ?: manga.status
            }

        val url = manga.url
        // Try to find the cover
        for (dir in baseDirs) {
            val cover = File("${dir.absolutePath}/$url", COVER_NAME)
            if (cover.exists()) {
                manga.thumbnail_url = cover.absolutePath
                break
            }
        }

        // Copy the cover from the first chapter found.
        if (manga.thumbnail_url == null) {
            val chapters = fetchChapterList(manga).toBlocking().first()
            if (chapters.isNotEmpty()) {
                try {
                    val dest = updateCover(chapters.last(), manga)
                    manga.thumbnail_url = dest?.absolutePath
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
        return Observable.just(manga)
    }

    fun updateMangaInfo(manga: SManga) {
        val directory = getBaseDirectories(context).mapNotNull { File(it, manga.url) }.find {
            it.exists()
        } ?: return
        val gson = GsonBuilder().setPrettyPrinting().create()
        val existingFileName = directory.listFiles()?.find { it.extension == "json" }?.name
        val file = File(directory, existingFileName ?: "info.json")
        file.writeText(gson.toJson(manga.toJson()))
    }

    fun SManga.toJson(): MangaJson {
        return MangaJson(title, author, artist, description, genre?.split(", ")?.toTypedArray())
    }

    data class MangaJson(
        val title: String,
        val author: String?,
        val artist: String?,
        val description: String?,
        val genre: Array<String>?
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MangaJson

            if (title != other.title) return false

            return true
        }

        override fun hashCode(): Int {
            return title.hashCode()
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters =
            getBaseDirectories(context).mapNotNull { File(it, manga.url).listFiles()?.toList() }
                .flatten().filter { it.isDirectory || isSupportedFile(it.extension) }
                .map { chapterFile ->
                    SChapter.create().apply {
                        url = "${manga.url}/${chapterFile.name}"
                        val chapName = if (chapterFile.isDirectory) {
                            chapterFile.name
                        } else {
                            chapterFile.nameWithoutExtension
                        }
                        val chapNameCut =
                            chapName.replace(manga.title, "", true).trim(' ', '-', '_')
                        name = if (chapNameCut.isEmpty()) chapName else chapNameCut
                        date_upload = chapterFile.lastModified()
                        ChapterRecognition.parseChapterNumber(this, manga)
                    }
                }.sortedWith(
                    Comparator { c1, c2 ->
                        val c = c2.chapter_number.compareTo(c1.chapter_number)
                        if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
                    }
                )

        return Observable.just(chapters)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.error(Exception("Unused"))
    }

    private fun isSupportedFile(extension: String): Boolean {
        return extension.toLowerCase() in SUPPORTED_ARCHIVE_TYPES
    }

    fun getFormat(chapter: SChapter): Format {
        val baseDirs = getBaseDirectories(context)

        for (dir in baseDirs) {
            val chapFile = File(dir, chapter.url)
            if (!chapFile.exists()) continue

            return getFormat(chapFile)
        }
        throw Exception("Chapter not found")
    }

    private fun getFormat(file: File): Format {
        val extension = file.extension
        return if (file.isDirectory) {
            Format.Directory(file)
        } else if (extension.equals("zip", true) || extension.equals("cbz", true)) {
            Format.Zip(file)
        } else if (extension.equals("rar", true) || extension.equals("cbr", true)) {
            Format.Rar(file)
        } else if (extension.equals("epub", true)) {
            Format.Epub(file)
        } else {
            throw Exception("Invalid chapter format")
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): File? {
        val format = getFormat(chapter)
        return when (format) {
            is Format.Directory -> {
                val entry = format.file.listFiles()
                    ?.sortedWith(
                        Comparator<File> { f1, f2 ->
                            f1.name.compareToCaseInsensitiveNaturalOrder(f2.name)
                        }
                    )
                    ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                entry?.let { updateCover(context, manga, it.inputStream()) }
            }
            is Format.Zip -> {
                ZipFile(format.file).use { zip ->
                    val entry = zip.entries().toList().sortedWith(
                        Comparator<ZipEntry> { f1, f2 ->
                            f1.name.compareToCaseInsensitiveNaturalOrder(f2.name)
                        }
                    ).find {
                        !it.isDirectory && ImageUtil.isImage(it.name) {
                            zip.getInputStream(it)
                        }
                    }

                    entry?.let { updateCover(context, manga, zip.getInputStream(it)) }
                }
            }
            is Format.Rar -> {
                Archive(format.file).use { archive ->
                    val entry = archive.fileHeaders.sortedWith(
                        Comparator<FileHeader> { f1, f2 ->
                            f1.fileNameString.compareToCaseInsensitiveNaturalOrder(f2.fileNameString)
                        }
                    ).find {
                        !it.isDirectory && ImageUtil.isImage(it.fileNameString) {
                            archive.getInputStream(it)
                        }
                    }

                    entry?.let { updateCover(context, manga, archive.getInputStream(it)) }
                }
            }
            is Format.Epub -> {
                EpubFile(format.file).use { epub ->
                    val entry = epub.getImagesFromPages().firstOrNull()?.let { epub.getEntry(it) }

                    entry?.let { updateCover(context, manga, epub.getInputStream(it)) }
                }
            }
        }
    }

    private class OrderBy :
        Filter.Sort("Order by", arrayOf("Title", "Date"), Filter.Sort.Selection(0, true))

    override fun getFilterList() = FilterList(OrderBy())

    sealed class Format {
        data class Directory(val file: File) : Format()
        data class Zip(val file: File) : Format()
        data class Rar(val file: File) : Format()
        data class Epub(val file: File) : Format()
    }
}
