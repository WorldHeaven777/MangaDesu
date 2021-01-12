package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.net.toUri
import coil.api.clear
import coil.api.loadAny
import coil.request.CachePolicy
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressBar
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.widget.GifViewTarget
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Holder of the webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
    private val frame: FrameLayout,
    viewer: WebtoonViewer
) : WebtoonBaseHolder(frame, viewer) {

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressBar = createProgressBar()

    /**
     * Progress bar container. Needed to keep a minimum height size of the holder, otherwise the
     * adapter would create more views to fill the screen, which is not wanted.
     */
    private lateinit var progressContainer: ViewGroup

    /**
     * Image view that supports subsampling on zoom.
     */
    private var subsamplingImageView: SubsamplingScaleImageView? = null

    /**
     * Simple image view only used on GIFs.
     */
    private var imageView: ImageView? = null

    /**
     * Retry button container used to allow retrying.
     */
    private var retryContainer: ViewGroup? = null

    /**
     * Error layout to show when the image fails to decode.
     */
    private var decodeErrorLayout: ViewGroup? = null

    /**
     * Getter to retrieve the height of the recycler view.
     */
    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var progressSubscription: Subscription? = null

    /**
     * Subscription used to read the header of the image. This is needed in order to instantiate
     * the appropiate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderSubscription: Subscription? = null

    init {
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        frame.setBackgroundColor(Color.BLACK)
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        observeStatus()
        refreshLayoutParams()
    }

    private fun refreshLayoutParams() {
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            val margin = Resources.getSystem().displayMetrics.widthPixels * (viewer.config.sidePadding / 100f)
            marginEnd = margin.toInt()
            marginStart = margin.toInt()
        }
        if (viewer.hasMargins) {
            frame.updatePaddingRelative(bottom = 15.dpToPx)
        }
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    override fun recycle() {
        unsubscribeStatus()
        unsubscribeProgress()
        unsubscribeReadImageHeader()

        removeDecodeErrorLayout()
        subsamplingImageView?.recycle()
        subsamplingImageView?.gone()
        imageView?.clear()
        imageView?.gone()
        progressBar.setProgress(0)
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        unsubscribeStatus()

        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { processStatus(it) }

        addSubscription(statusSubscription)
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        unsubscribeProgress()

        val page = page ?: return

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { page.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value -> progressBar.setProgress(value) }

        addSubscription(progressSubscription)
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Int) {
        when (status) {
            Page.QUEUE -> setQueued()
            Page.LOAD_PAGE -> setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress()
                setDownloading()
            }
            Page.READY -> {
                setImage()
                unsubscribeProgress()
            }
            Page.ERROR -> {
                setError()
                unsubscribeProgress()
            }
        }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus() {
        removeSubscription(statusSubscription)
        statusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress() {
        removeSubscription(progressSubscription)
        progressSubscription = null
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun unsubscribeReadImageHeader() {
        removeSubscription(readImageHeaderSubscription)
        readImageHeaderSubscription = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.visible()
        progressBar.visible()
        retryContainer?.gone()
        removeDecodeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.visible()
        progressBar.visible()
        retryContainer?.gone()
        removeDecodeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.visible()
        progressBar.visible()
        retryContainer?.gone()
        removeDecodeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressContainer.visible()
        progressBar.visible()
        progressBar.completeAndFadeOut()
        retryContainer?.gone()
        removeDecodeErrorLayout()

        unsubscribeReadImageHeader()
        val streamFn = page?.stream ?: return

        var openStream: InputStream? = null
        readImageHeaderSubscription = Observable
            .fromCallable {
                val stream = streamFn().buffered(16)
                openStream = stream

                ImageUtil.findImageType(stream) == ImageUtil.ImageType.GIF
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { isAnimated ->
                if (!isAnimated) {
                    val subsamplingView = initSubsamplingImageView()
                    subsamplingView.visible()
                    subsamplingView.setImage(ImageSource.inputStream(openStream!!))
                } else {
                    val imageView = initImageView()
                    imageView.visible()
                    imageView.setImage(openStream!!)
                }
            }
            // Keep the Rx stream alive to close the input stream only when unsubscribed
            .flatMap { Observable.never<Unit>() }
            .doOnUnsubscribe { openStream?.close() }
            .subscribe({}, {})

        addSubscription(readImageHeaderSubscription)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressContainer.gone()
        initRetryLayout().visible()
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.gone()
    }

    /**
     * Called when the image fails to decode.
     */
    private fun onImageDecodeError() {
        progressContainer.gone()
        initDecodeErrorLayout().visible()
    }

    /**
     * Creates a new progress bar.
     */
    @SuppressLint("PrivateResource")
    private fun createProgressBar(): ReaderProgressBar {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressBar(context).apply {
            val size = 48.dpToPx
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, parentHeight / 4, 0, 0)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    /**
     * Initializes a subsampling scale view.
     */
    private fun initSubsamplingImageView(): SubsamplingScaleImageView {
        if (subsamplingImageView != null) return subsamplingImageView!!

        val config = viewer.config

        subsamplingImageView = WebtoonSubsamplingImageView(context).apply {
            setMaxTileSize(viewer.activity.maxBitmapSize)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setMinimumDpi(90)
            setMinimumTileDpi(180)
            setCropBorders(if (viewer.hasMargins) config.verticalCropBorders else config.webtoonCropBorders)
            setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        onImageDecoded()
                    }

                    override fun onImageLoadError(e: Exception) {
                        onImageDecodeError()
                    }
                }
            )
        }
        frame.addView(subsamplingImageView, MATCH_PARENT, MATCH_PARENT)
        return subsamplingImageView!!
    }

    /**
     * Initializes an image view, used for GIFs.
     */
    private fun initImageView(): ImageView {
        if (imageView != null) return imageView!!

        imageView = AppCompatImageView(context).apply {
            adjustViewBounds = true
        }
        frame.addView(imageView, MATCH_PARENT, MATCH_PARENT)
        return imageView!!
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initRetryLayout(): ViewGroup {
        if (retryContainer != null) return retryContainer!!

        retryContainer = FrameLayout(context)
        frame.addView(retryContainer, MATCH_PARENT, parentHeight)

        AppCompatButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, parentHeight / 4, 0, 0)
            }
            setText(R.string.retry)
            setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }

            retryContainer!!.addView(this)
        }
        return retryContainer!!
    }

    /**
     * Initializes a decode error layout.
     */
    private fun initDecodeErrorLayout(): ViewGroup {
        if (decodeErrorLayout != null) return decodeErrorLayout!!

        val margins = 8.dpToPx

        val decodeLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, parentHeight).apply {
                setMargins(0, parentHeight / 6, 0, 0)
            }
            gravity = Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.VERTICAL
        }
        decodeErrorLayout = decodeLayout

        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(0, margins, 0, margins)
            }
            gravity = Gravity.CENTER
            setText(R.string.decode_image_error)

            decodeLayout.addView(this)
        }

        AppCompatButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(0, margins, 0, margins)
            }
            setText(R.string.retry)
            setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }

            decodeLayout.addView(this)
        }

        val imageUrl = page?.imageUrl
        if (imageUrl != null && imageUrl.startsWith("http")) {
            AppCompatButton(context).apply {
                layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(0, margins, 0, margins)
                }
                setText(R.string.open_in_browser)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, imageUrl.toUri())
                    context.startActivity(intent)
                }

                decodeLayout.addView(this)
            }
        }

        frame.addView(decodeLayout)
        return decodeLayout
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeDecodeErrorLayout() {
        val layout = decodeErrorLayout
        if (layout != null) {
            frame.removeView(layout)
            decodeErrorLayout = null
        }
    }

    /**
     * Extension method to set a [stream] into this ImageView.
     */
    private fun ImageView.setImage(stream: InputStream) {
        this.loadAny(stream.readBytes()) {
            memoryCachePolicy(CachePolicy.DISABLED)
            diskCachePolicy(CachePolicy.DISABLED)
            target(GifViewTarget(this@setImage, progressBar, decodeErrorLayout))
        }
    }
}
