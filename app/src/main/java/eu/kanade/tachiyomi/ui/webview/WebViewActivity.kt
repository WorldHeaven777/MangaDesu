package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.WebViewClientCompat
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.android.synthetic.main.webview_activity.*
import uy.kohesive.injekt.injectLazy

open class WebViewActivity : BaseWebViewActivity() {

    private val sourceManager by injectLazy<SourceManager>()
    private var bundle: Bundle? = null

    companion object {
        const val SOURCE_KEY = "source_key"
        const val URL_KEY = "url_key"
        const val TITLE_KEY = "title_key"

        fun newIntent(context: Context, sourceId: Long, url: String, title: String?): Intent {
            val intent = Intent(context, WebViewActivity::class.java)
            intent.putExtra(SOURCE_KEY, sourceId)
            intent.putExtra(URL_KEY, url)
            intent.putExtra(TITLE_KEY, title)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.extras?.getString(TITLE_KEY)
        val container: ViewGroup = findViewById(R.id.web_view_layout)
        val content: LinearLayout = findViewById(R.id.web_linear_layout)
        container.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        content.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        swipe_refresh.isEnabled = false

        if (bundle == null) {
            val source = sourceManager.get(intent.extras!!.getLong(SOURCE_KEY)) as? HttpSource ?: return
            val url = intent.extras!!.getString(URL_KEY) ?: return
            val headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }

            webview.webViewClient = object : WebViewClientCompat() {
                override fun shouldOverrideUrlCompat(view: WebView, url: String): Boolean {
                    view.loadUrl(url)
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    invalidateOptionsMenu()
                    title = view?.title
                    swipe_refresh.isEnabled = true
                    swipe_refresh?.isRefreshing = false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    invalidateOptionsMenu()
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    nested_view.scrollTo(0, 0)
                }
            }

            webview.settings.userAgentString = source.headers["User-Agent"]
            webview.loadUrl(url, headers)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        invalidateOptionsMenu()
    }

    /**
     * Called when the options menu of the toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.webview, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val backItem = toolbar.menu.findItem(R.id.action_web_back)
        val forwardItem = toolbar.menu.findItem(R.id.action_web_forward)
        backItem?.isEnabled = webview.canGoBack()
        forwardItem?.isEnabled = webview.canGoForward()
        val hasHistory = webview.canGoBack() || webview.canGoForward()
        backItem?.isVisible = hasHistory
        forwardItem?.isVisible = hasHistory
        val tintColor = getResourceColor(R.attr.actionBarTintColor)
        val translucentWhite = ColorUtils.setAlphaComponent(tintColor, 127)
        backItem.icon?.setTint(if (webview.canGoBack()) tintColor else translucentWhite)
        forwardItem?.icon?.setTint(if (webview.canGoForward()) tintColor else translucentWhite)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) webview.goBack()
        else super.onBackPressed()
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_web_back -> webview.goBack()
            R.id.action_web_forward -> webview.goForward()
            R.id.action_web_share -> shareWebpage()
            R.id.action_web_browser -> openInBrowser()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shareWebpage() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, webview.url)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    private fun openInBrowser() {
        openInBrowser(webview.url)
    }
}
