/*
 * Copyright © 2017 Jorge Martín Espinosa
 */

package com.arasthel.spannedgridlayoutmanager

import android.graphics.PointF
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/**
 * A spanned grid layout manager that can scroll on both axes.
 *
 * [visibleSpans] controls how many spans fit in the RecyclerView width.
 * [contentSpans] controls how many spans exist in the full horizontal content.
 */
open class TwoWaySpannedGridLayoutManager(
    val visibleSpans: Int,
    val contentSpans: Int = visibleSpans
) : RecyclerView.LayoutManager(), RecyclerView.SmoothScroller.ScrollVectorProvider {

    init {
        if (visibleSpans < 1) {
            throw InvalidMaxSpansException(visibleSpans)
        }
        if (contentSpans < visibleSpans) {
            throw InvalidContentSpansException(contentSpans, visibleSpans)
        }
    }

    private val childFrames = mutableMapOf<Int, Rect>()

    private var itemSize = 0
    private var contentWidth = 0
    private var contentHeight = 0
    private var scrollX = 0
    private var scrollY = 0
    private var pendingScrollToPosition: Int? = null

    var itemOrderIsStable = false

    var spanSizeLookup: SpanSizeLookup? = null
        set(newValue) {
            field = newValue
            requestLayout()
        }

    open val firstVisiblePosition: Int
        get() {
            if (childCount == 0) return 0
            var minPosition = Int.MAX_VALUE
            for (i in 0 until childCount) {
                minPosition = min(minPosition, getPosition(getChildAt(i)!!))
            }
            return if (minPosition == Int.MAX_VALUE) 0 else minPosition
        }

    open val lastVisiblePosition: Int
        get() {
            if (childCount == 0) return 0
            var maxPosition = 0
            for (i in 0 until childCount) {
                maxPosition = max(maxPosition, getPosition(getChildAt(i)!!))
            }
            return maxPosition
        }

    open val horizontalScrollOffset: Int
        get() = scrollX

    open val verticalScrollOffset: Int
        get() = scrollY

    open class SpanSizeLookup(
        var lookupFunction: ((Int) -> SpanSize)? = null
    ) {
        private var cache = SparseArray<SpanSize>()

        var usesCache = false

        fun getSpanSize(position: Int): SpanSize {
            if (usesCache) {
                val cachedValue = cache[position]
                if (cachedValue != null) return cachedValue

                val value = getSpanSizeFromFunction(position)
                cache.put(position, value)
                return value
            }

            return getSpanSizeFromFunction(position)
        }

        private fun getSpanSizeFromFunction(position: Int): SpanSize {
            return lookupFunction?.invoke(position) ?: getDefaultSpanSize()
        }

        protected open fun getDefaultSpanSize(): SpanSize {
            return SpanSize(1, 1)
        }

        fun invalidateCache() {
            cache.clear()
        }
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (state.itemCount == 0) {
            childFrames.clear()
            contentWidth = 0
            contentHeight = 0
            scrollX = 0
            scrollY = 0
            removeAndRecycleAllViews(recycler)
            return
        }

        itemSize = max(1, horizontalSpace / visibleSpans)
        buildChildFrames(state.itemCount)
        applyPendingScrollPosition()
        clampScrollOffsets()

        detachAndScrapAttachedViews(recycler)
        fillVisibleChildren(recycler, state)
    }

    private fun buildChildFrames(itemCount: Int) {
        childFrames.clear()

        val rectsHelper = TwoWayRectsHelper(contentSpans)
        var maxBottomSpan = 0

        for (position in 0 until itemCount) {
            val spanSize = spanSizeLookup?.getSpanSize(position) ?: SpanSize(1, 1)
            validateSpanSize(spanSize)

            val spanRect = rectsHelper.findRect(spanSize)
            rectsHelper.pushRect(spanRect)
            maxBottomSpan = max(maxBottomSpan, spanRect.bottom)

            childFrames[position] = Rect(
                spanRect.left * itemSize,
                spanRect.top * itemSize,
                spanRect.right * itemSize,
                spanRect.bottom * itemSize
            )
        }

        contentWidth = max(horizontalSpace, contentSpans * itemSize)
        contentHeight = max(verticalSpace, maxBottomSpan * itemSize)
    }

    private fun validateSpanSize(spanSize: SpanSize) {
        if (spanSize.width < 1 || spanSize.width > contentSpans) {
            throw InvalidSpanSizeException(spanSize.width, contentSpans)
        }
        if (spanSize.height < 1) {
            throw InvalidTwoWaySpanHeightException(spanSize.height)
        }
    }

    private fun applyPendingScrollPosition() {
        val position = pendingScrollToPosition ?: return
        val frame = childFrames[position] ?: return

        scrollX = frame.left
        scrollY = frame.top
        pendingScrollToPosition = null
    }

    private fun clampScrollOffsets() {
        scrollX = scrollX.coerceIn(0, maxScrollX())
        scrollY = scrollY.coerceIn(0, maxScrollY())
    }

    private fun fillVisibleChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        recycleInvisibleChildren(recycler)

        val viewport = visibleContentRect()
        for (position in 0 until state.itemCount) {
            val frame = childFrames[position] ?: continue
            if (!viewport.intersects(frame) || findViewByPosition(position) != null) continue

            val view = recycler.getViewForPosition(position)
            addView(view)
            measureChildForFrame(view, frame)
            layoutChild(view, frame)
        }
    }

    private fun recycleInvisibleChildren(recycler: RecyclerView.Recycler) {
        val viewport = visibleContentRect()
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i) ?: continue
            val frame = childFrames[getPosition(child)]
            if (frame == null || !viewport.intersects(frame)) {
                removeAndRecycleView(child, recycler)
            }
        }
    }

    private fun visibleContentRect(): Rect {
        return Rect(
            scrollX - itemSize,
            scrollY - itemSize,
            scrollX + horizontalSpace + itemSize,
            scrollY + verticalSpace + itemSize
        )
    }

    private fun measureChildForFrame(view: View, frame: Rect) {
        val insetsRect = Rect()
        calculateItemDecorationsForChild(view, insetsRect)

        val layoutParams = view.layoutParams
        layoutParams.width = max(0, frame.width() - insetsRect.left - insetsRect.right)
        layoutParams.height = max(0, frame.height() - insetsRect.top - insetsRect.bottom)

        measureChildWithMargins(view, 0, 0)
    }

    private fun layoutChild(view: View, frame: Rect) {
        layoutDecorated(
            view,
            paddingLeft + frame.left - scrollX,
            paddingTop + frame.top - scrollY,
            paddingLeft + frame.right - scrollX,
            paddingTop + frame.bottom - scrollY
        )
    }

    override fun canScrollHorizontally(): Boolean {
        return contentWidth > horizontalSpace
    }

    override fun canScrollVertically(): Boolean {
        return contentHeight > verticalSpace
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (dx == 0 || state.itemCount == 0) return 0

        val travelled = scrollBy(dx, Axis.HORIZONTAL)
        if (travelled == 0) return 0

        offsetChildrenHorizontal(-travelled)
        fillVisibleChildren(recycler, state)
        return travelled
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (dy == 0 || state.itemCount == 0) return 0

        val travelled = scrollBy(dy, Axis.VERTICAL)
        if (travelled == 0) return 0

        offsetChildrenVertical(-travelled)
        fillVisibleChildren(recycler, state)
        return travelled
    }

    private fun scrollBy(delta: Int, axis: Axis): Int {
        return if (axis == Axis.HORIZONTAL) {
            val previous = scrollX
            scrollX = (scrollX + delta).coerceIn(0, maxScrollX())
            scrollX - previous
        } else {
            val previous = scrollY
            scrollY = (scrollY + delta).coerceIn(0, maxScrollY())
            scrollY - previous
        }
    }

    override fun scrollToPosition(position: Int) {
        pendingScrollToPosition = position
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
                return this@TwoWaySpannedGridLayoutManager.computeScrollVectorForPosition(targetPosition)
            }

            override fun getHorizontalSnapPreference(): Int {
                return SNAP_TO_START
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }

        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        val frame = childFrames[targetPosition] ?: return null
        val dx = frame.left - scrollX
        val dy = frame.top - scrollY
        return PointF(
            dx.compareTo(0).toFloat(),
            dy.compareTo(0).toFloat()
        )
    }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int {
        return scrollX
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        return scrollY
    }

    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int {
        return horizontalSpace
    }

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int {
        return verticalSpace
    }

    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int {
        return contentWidth
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        return contentHeight
    }

    override fun onSaveInstanceState(): Parcelable? {
        return if (itemOrderIsStable) {
            SavedState(scrollX, scrollY)
        } else {
            null
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as? SavedState ?: return
        scrollX = savedState.scrollX
        scrollY = savedState.scrollY
        requestLayout()
    }

    private val horizontalSpace: Int
        get() = max(0, width - paddingLeft - paddingRight)

    private val verticalSpace: Int
        get() = max(0, height - paddingTop - paddingBottom)

    private fun maxScrollX(): Int {
        return max(0, contentWidth - horizontalSpace)
    }

    private fun maxScrollY(): Int {
        return max(0, contentHeight - verticalSpace)
    }

    private enum class Axis {
        HORIZONTAL, VERTICAL
    }

    class SavedState(val scrollX: Int, val scrollY: Int) : Parcelable {
        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source.readInt(), source.readInt())
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(scrollX)
            dest.writeInt(scrollY)
        }

        override fun describeContents(): Int {
            return 0
        }
    }
}

private class TwoWayRectsHelper(private val contentSpans: Int) {

    private val rectComparator = Comparator<Rect> { rect1, rect2 ->
        if (rect1.top == rect2.top) {
            if (rect1.left < rect2.left) -1 else 1
        } else {
            if (rect1.top < rect2.top) -1 else 1
        }
    }

    private val freeRects = mutableListOf<Rect>()

    init {
        freeRects.add(Rect(0, 0, contentSpans, Int.MAX_VALUE))
    }

    fun findRect(spanSize: SpanSize): Rect {
        val lane = freeRects.first {
            val itemRect = Rect(
                it.left,
                it.top,
                it.left + spanSize.width,
                it.top + spanSize.height
            )
            it.contains(itemRect)
        }

        return Rect(
            lane.left,
            lane.top,
            lane.left + spanSize.width,
            lane.top + spanSize.height
        )
    }

    fun pushRect(rect: Rect) {
        subtract(rect)
    }

    private fun subtract(subtractedRect: Rect) {
        val interestingRects = freeRects.filter {
            it.isAdjacentTo(subtractedRect) || it.intersects(subtractedRect)
        }

        val possibleNewRects = mutableListOf<Rect>()
        val adjacentRects = mutableListOf<Rect>()

        for (free in interestingRects) {
            if (free.isAdjacentTo(subtractedRect) && !subtractedRect.contains(free)) {
                adjacentRects.add(free)
            } else {
                freeRects.remove(free)

                if (free.left < subtractedRect.left) {
                    possibleNewRects.add(Rect(free.left, free.top, subtractedRect.left, free.bottom))
                }

                if (free.right > subtractedRect.right) {
                    possibleNewRects.add(Rect(subtractedRect.right, free.top, free.right, free.bottom))
                }

                if (free.top < subtractedRect.top) {
                    possibleNewRects.add(Rect(free.left, free.top, free.right, subtractedRect.top))
                }

                if (free.bottom > subtractedRect.bottom) {
                    possibleNewRects.add(Rect(free.left, subtractedRect.bottom, free.right, free.bottom))
                }
            }
        }

        for (rect in possibleNewRects) {
            val isAdjacent = adjacentRects.firstOrNull { it != rect && it.contains(rect) } != null
            if (isAdjacent) continue

            val isContained = possibleNewRects.firstOrNull { it != rect && it.contains(rect) } != null
            if (isContained) continue

            freeRects.add(rect)
        }

        freeRects.sortWith(rectComparator)
    }
}

class InvalidContentSpansException(contentSpans: Int, visibleSpans: Int) :
    RuntimeException("Invalid content spans: $contentSpans. Content spans must be at least visible spans: $visibleSpans.")

class InvalidTwoWaySpanHeightException(errorSize: Int) :
    RuntimeException("Invalid item span height: $errorSize. Span height must be at least 1.")
