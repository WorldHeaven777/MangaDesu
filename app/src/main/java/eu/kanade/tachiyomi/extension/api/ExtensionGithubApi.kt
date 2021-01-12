package eu.kanade.tachiyomi.extension.api

import android.content.Context
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonArray
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

internal class ExtensionGithubApi {

    private val network: NetworkHelper by injectLazy()

    private val gson: Gson by injectLazy()

    suspend fun findExtensions(): List<Extension.Available> {
        val call = GET("$REPO_URL/index.json")

        return withContext(Dispatchers.IO) {
            parseResponse(network.client.newCall(call).await())
        }
    }

    suspend fun checkForUpdates(context: Context): List<Extension.Installed> {
        return withContext(Dispatchers.IO) {
            val call = GET("$REPO_URL/index.json")
            val response = network.client.newCall(call).await()

            if (response.isSuccessful) {
                val extensions = parseResponse(response)
                val extensionsWithUpdate = mutableListOf<Extension.Installed>()

                val installedExtensions =
                    ExtensionLoader.loadExtensions(context).filterIsInstance<LoadResult.Success>()
                        .map { it.extension }
                val mutInstalledExtensions = installedExtensions.toMutableList()
                for (installedExt in mutInstalledExtensions) {
                    val pkgName = installedExt.pkgName
                    val availableExt = extensions.find { it.pkgName == pkgName } ?: continue

                    val hasUpdate = availableExt.versionCode > installedExt.versionCode
                    if (hasUpdate) extensionsWithUpdate.add(installedExt)
                }

                extensionsWithUpdate
            } else {
                response.close()
                throw Exception("Failed to get extensions")
            }
        }
    }

    private fun parseResponse(response: Response): List<Extension.Available> {
        val text = response.body?.use { it.string() } ?: return emptyList()

        val json = gson.fromJson<JsonArray>(text)

        return json.map { element ->
            val name = element["name"].string.substringAfter("Tachiyomi: ")
            val pkgName = element["pkg"].string
            val apkName = element["apk"].string
            val versionName = element["version"].string
            val versionCode = element["code"].int
            val lang = element["lang"].string
            val icon = "$REPO_URL/icon/${apkName.replace(".apk", ".png")}"

            Extension.Available(name, pkgName, versionName, versionCode, lang, apkName, icon)
        }
    }

    fun getApkUrl(extension: Extension.Available): String {
        return "$REPO_URL/apk/${extension.apkName}"
    }

    companion object {
        private const val REPO_URL =
            "https://raw.githubusercontent.com/inorichi/tachiyomi-extensions/repo"
    }
}
