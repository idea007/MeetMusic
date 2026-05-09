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
import kotlin.math.roundToInt

/**
 * A spanned grid layout manager that can scroll on both axes and grow in every direction.
 *
 * [visibleSpans] controls how many spans fit in the RecyclerView width.
 * [contentSpans] controls the initial horizontal content width.
 * [pageSpans] controls how many spans are added when content grows left or right.
 */
open class TwoWaySpannedGridLayoutManager(
    val visibleSpans: Int,
    val contentSpans: Int = visibleSpans,
    val pageSpans: Int = visibleSpans
) : RecyclerView.LayoutManager(), RecyclerView.SmoothScroller.ScrollVectorProvider {

    enum class InsertDirection {
        LEFT, UP, RIGHT, DOWN
    }

    enum class DragAxis {
        NONE, HORIZONTAL, VERTICAL
    }

    init {
        if (visibleSpans < 1) {
            throw InvalidMaxSpansException(visibleSpans)
        }
        if (contentSpans < visibleSpans) {
            throw InvalidContentSpansException(contentSpans, visibleSpans)
        }
        if (pageSpans < 1) {
            throw InvalidMaxSpansException(pageSpans)
        }
    }

    private val spanFrames = mutableMapOf<Int, Rect>()
    private val contentBounds = Rect(0, 0, 0, 0)

    private var itemSize = 0
    private var scrollX = 0
    private var scrollY = 0
    private var knownItemCount = 0
    private var pendingScrollToPosition: Int? = null
    private var pendingAppendDirection = InsertDirection.DOWN
    private var pendingPrependDirection = InsertDirection.UP
    private var dragAxis = DragAxis.NONE

    var itemOrderIsStable = false

    var spanSizeLookup: SpanSizeLookup? = null
        set(newValue) {
            field = newValue
            clearLayoutState(resetScroll = true)
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

    fun prepareForAppend(direction: InsertDirection) {
        pendingAppendDirection = direction
    }

    fun prepareForPrepend(direction: InsertDirection) {
        pendingPrependDirection = direction
    }

    fun setDragAxis(axis: DragAxis) {
        dragAxis = axis
    }

    fun clearDragAxis() {
        dragAxis = DragAxis.NONE
    }

    fun isNearLeftEdge(thresholdSpans: Int = LOAD_MORE_THRESHOLD_SPANS): Boolean {
        return itemSize > 0 && scrollX - minScrollX() <= thresholdSpans * itemSize
    }

    fun isNearRightEdge(thresholdSpans: Int = LOAD_MORE_THRESHOLD_SPANS): Boolean {
        return itemSize > 0 && maxScrollX() - scrollX <= thresholdSpans * itemSize
    }

    fun isNearTopEdge(thresholdSpans: Int = LOAD_MORE_THRESHOLD_SPANS): Boolean {
        return itemSize > 0 && scrollY - minScrollY() <= thresholdSpans * itemSize
    }

    fun isNearBottomEdge(thresholdSpans: Int = LOAD_MORE_THRESHOLD_SPANS): Boolean {
        return itemSize > 0 && maxScrollY() - scrollY <= thresholdSpans * itemSize
    }

    fun snapToNearestSpan(recyclerView: RecyclerView) {
        if (itemSize <= 0) return

        val targetX = (scrollX.toFloat() / itemSize).roundToInt() * itemSize
        val targetY = (scrollY.toFloat() / itemSize).roundToInt() * itemSize
        val correctedX = targetX.coerceIn(minScrollX(), maxScrollX())
        val correctedY = targetY.coerceIn(minScrollY(), maxScrollY())
        val dx = correctedX - scrollX
        val dy = correctedY - scrollY

        if (dx != 0 || dy != 0) {
            recyclerView.smoothScrollBy(dx, dy)
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
            clearLayoutState(resetScroll = true)
            removeAndRecycleAllViews(recycler)
            return
        }

        itemSize = max(1, horizontalSpace / visibleSpans)
        ensureFramesForItemCount(state.itemCount)
        applyPendingScrollPosition()
        clampScrollOffsets()

        detachAndScrapAttachedViews(recycler)
        fillVisibleChildren(recycler, state)
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        if (itemCount <= 0) return

        val direction = if (positionStart == 0) pendingPrependDirection else pendingAppendDirection
        val anchor = captureAnchor(if (positionStart == 0) itemCount else 0)
        growContentBounds(direction, itemCount, positionStart == 0)
        rebuildFrames(knownItemCount + itemCount)
        restoreAnchor(anchor)
        requestLayout()
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        clearLayoutState(resetScroll = true)
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        clearLayoutState(resetScroll = true)
    }

    private fun ensureFramesForItemCount(itemCount: Int) {
        if (spanFrames.isEmpty() || knownItemCount > itemCount) {
            clearLayoutState(resetScroll = true)
            ensureInitialContentBounds()
            rebuildFrames(itemCount)
            return
        }

        if (spanFrames.size < itemCount) {
            growContentBounds(InsertDirection.DOWN, itemCount - spanFrames.size, false)
            rebuildFrames(itemCount)
        }
    }

    private fun rebuildFrames(itemCount: Int) {
        ensureInitialContentBounds()

        val layoutArea = Rect(contentBounds.left, contentBounds.top, contentBounds.right, Int.MAX_VALUE)
        val rectsHelper = TwoWayRectsHelper(layoutArea)
        var bottom = contentBounds.top

        spanFrames.clear()
        for (position in 0 until itemCount) {
            val spanSize = spanSizeLookup?.getSpanSize(position) ?: SpanSize(1, 1)
            validateSpanSize(spanSize, layoutArea.width())

            val spanRect = rectsHelper.findRect(spanSize)
            rectsHelper.pushRect(spanRect)
            spanFrames[position] = spanRect
            bottom = max(bottom, spanRect.bottom)
        }

        contentBounds.bottom = bottom
        knownItemCount = itemCount
    }

    private fun ensureInitialContentBounds() {
        if (contentBounds.width() <= 0) {
            contentBounds.left = 0
            contentBounds.right = contentSpans
        }
    }

    private fun validateSpanSize(spanSize: SpanSize, maxWidthSpans: Int) {
        if (spanSize.width < 1 || spanSize.width > maxWidthSpans) {
            throw InvalidSpanSizeException(spanSize.width, maxWidthSpans)
        }
        if (spanSize.height < 1) {
            throw InvalidTwoWaySpanHeightException(spanSize.height)
        }
    }

    private fun growContentBounds(direction: InsertDirection, itemCount: Int, isPrepend: Boolean) {
        ensureInitialContentBounds()

        when (direction) {
            InsertDirection.LEFT -> contentBounds.left -= pageSpans
            InsertDirection.RIGHT -> contentBounds.right += pageSpans
            InsertDirection.UP -> contentBounds.top -= estimateRowsForItems(itemCount)
            InsertDirection.DOWN -> {
                if (isPrepend) {
                    contentBounds.top -= estimateRowsForItems(itemCount)
                }
            }
        }
    }

    private fun estimateRowsForItems(itemCount: Int): Int {
        val widthSpans = max(1, contentBounds.width())
        var totalArea = 0
        for (position in 0 until itemCount) {
            val spanSize = spanSizeLookup?.getSpanSize(position) ?: SpanSize(1, 1)
            totalArea += spanSize.width * spanSize.height
        }
        return max(1, (totalArea + widthSpans - 1) / widthSpans)
    }

    private fun captureAnchor(positionOffset: Int): Anchor? {
        if (childCount == 0 || itemSize <= 0) return null

        val position = firstVisiblePosition
        val frame = spanFrames[position] ?: return null
        return Anchor(position + positionOffset, frame.left * itemSize, frame.top * itemSize)
    }

    private fun restoreAnchor(anchor: Anchor?) {
        anchor ?: return

        val frame = spanFrames[anchor.position] ?: return
        scrollX += frame.left * itemSize - anchor.left
        scrollY += frame.top * itemSize - anchor.top
        clampScrollOffsets()
    }

    private fun clearLayoutState(resetScroll: Boolean) {
        spanFrames.clear()
        contentBounds.set(0, 0, 0, 0)
        knownItemCount = 0
        pendingScrollToPosition = null
        pendingAppendDirection = InsertDirection.DOWN
        pendingPrependDirection = InsertDirection.UP

        if (resetScroll) {
            scrollX = 0
            scrollY = 0
        }
    }

    private fun applyPendingScrollPosition() {
        val position = pendingScrollToPosition ?: return
        val frame = spanFrames[position] ?: return

        scrollX = frame.left * itemSize
        scrollY = frame.top * itemSize
        pendingScrollToPosition = null
    }

    private fun clampScrollOffsets() {
        scrollX = scrollX.coerceIn(minScrollX(), maxScrollX())
        scrollY = scrollY.coerceIn(minScrollY(), maxScrollY())
    }

    private fun fillVisibleChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        recycleInvisibleChildren(recycler)

        val viewport = visibleContentRect()
        for (position in 0 until state.itemCount) {
            val frame = spanFrames[position] ?: continue
            val pixelFrame = frame.toPixelRect()
            if (!viewport.intersects(pixelFrame) || findViewByPosition(position) != null) continue

            val view = recycler.getViewForPosition(position)
            addView(view)
            measureChildForFrame(view, pixelFrame)
            layoutChild(view, pixelFrame)
        }
    }

    private fun recycleInvisibleChildren(recycler: RecyclerView.Recycler) {
        val viewport = visibleContentRect()
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i) ?: continue
            val frame = spanFrames[getPosition(child)]?.toPixelRect()
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
        if (dragAxis == DragAxis.VERTICAL) return 0
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
        if (dragAxis == DragAxis.HORIZONTAL) return 0
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
            scrollX = (scrollX + delta).coerceIn(minScrollX(), maxScrollX())
            scrollX - previous
        } else {
            val previous = scrollY
            scrollY = (scrollY + delta).coerceIn(minScrollY(), maxScrollY())
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
        val frame = spanFrames[targetPosition] ?: return null
        val dx = frame.left * itemSize - scrollX
        val dy = frame.top * itemSize - scrollY
        return PointF(
            dx.compareTo(0).toFloat(),
            dy.compareTo(0).toFloat()
        )
    }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int {
        return scrollX - minScrollX()
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        return scrollY - minScrollY()
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

    private val contentWidth: Int
        get() = max(horizontalSpace, contentBounds.width() * itemSize)

    private val contentHeight: Int
        get() = max(verticalSpace, contentBounds.height() * itemSize)

    private fun minScrollX(): Int {
        return contentBounds.left * itemSize
    }

    private fun maxScrollX(): Int {
        return max(minScrollX(), contentBounds.right * itemSize - horizontalSpace)
    }

    private fun minScrollY(): Int {
        return contentBounds.top * itemSize
    }

    private fun maxScrollY(): Int {
        return max(minScrollY(), contentBounds.bottom * itemSize - verticalSpace)
    }

    private fun Rect.toPixelRect(): Rect {
        return Rect(left * itemSize, top * itemSize, right * itemSize, bottom * itemSize)
    }

    private enum class Axis {
        HORIZONTAL, VERTICAL
    }

    private data class Anchor(
        val position: Int,
        val left: Int,
        val top: Int
    )

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

    companion object {
        private const val LOAD_MORE_THRESHOLD_SPANS = 2
    }
}

private class TwoWayRectsHelper(private val area: Rect) {

    private val rectComparator = Comparator<Rect> { rect1, rect2 ->
        if (rect1.top == rect2.top) {
            if (rect1.left < rect2.left) -1 else 1
        } else {
            if (rect1.top < rect2.top) -1 else 1
        }
    }

    private val freeRects = mutableListOf<Rect>()

    init {
        freeRects.add(Rect(area))
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
