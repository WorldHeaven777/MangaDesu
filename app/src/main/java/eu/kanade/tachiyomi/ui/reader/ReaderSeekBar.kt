package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

/**
 * Seekbar to show current chapter progress.
 */
class ReaderSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatSeekBar(context, attrs) {

    /**
     * Whether the seekbar should draw from right to left.
     */
    var isRTL = false
    private val boundingBox: Rect = Rect()
    private val exclusions = listOf(boundingBox)

    /**
     * Draws the seekbar, translating the canvas if using a right to left reader.
     */
    override fun draw(canvas: Canvas) {
        if (isRTL) {
            val px = width / 2f
            val py = height / 2f

            canvas.scale(-1f, 1f, px, py)
        }
        super.draw(canvas)
    }

    /**
     * Handles touch events, translating coordinates if using a right to left reader.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isRTL) {
            event.setLocation(width - event.x, event.y)
        }
        return super.onTouchEvent(event)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29 && changed) {
            boundingBox.set(left, top, right, bottom)
            systemGestureExclusionRects = exclusions
        }
    }
}
