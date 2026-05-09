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
    private val occupiedBounds = Rect(0, 0, 0, 0)
    private var occupiedBoundsDirty = true

    private var itemSize = 0
    private var scrollX = 0
    private var scrollY = 0
    private var knownItemCount = 0
    private var pendingScrollToPosition: Int? = null
    private var pendingAppendDirection = InsertDirection.DOWN
    private var pendingPrependDirection = InsertDirection.UP
    private val pendingBlankFillAreas = mutableListOf<Rect>()
    private var dragAxis = DragAxis.NONE

    var itemOrderIsStable = false
    var onScrollBlocked: ((InsertDirection) -> Unit)? = null

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
        prepareVisibleBlankFillAreas()
    }

    fun prepareForPrepend(direction: InsertDirection) {
        pendingPrependDirection = direction
        prepareVisibleBlankFillAreas()
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

    fun findVisibleBlankDirections(minBlankSizePx: Int = max(1, itemSize / 2)): List<InsertDirection> {
        if (itemSize <= 0 || horizontalSpace <= 0 || verticalSpace <= 0) {
            return emptyList()
        }

        val viewport = visibleSpanRect() ?: return emptyList()
        val minBlankSpans = max(1, ceilDiv(max(1, minBlankSizePx), itemSize))
        for (blankArea in findVisibleBlankSpanAreas(viewport)) {
            if (blankArea.width() >= minBlankSpans || blankArea.height() >= minBlankSpans) {
                return listOf(InsertDirection.DOWN)
            }
        }

        return emptyList()
    }

    fun snapToNearestSpan(recyclerView: RecyclerView) {
        if (itemSize <= 0) return

        val targetX = (scrollX.toFloat() / itemSize).roundToInt() * itemSize
        val targetY = (scrollY.toFloat() / itemSize).roundToInt() * itemSize
        val horizontalBounds = visibleSliceScrollBounds(Axis.HORIZONTAL)
        val verticalBounds = visibleSliceScrollBounds(Axis.VERTICAL)
        val correctedX = if (horizontalBounds != null) {
            targetX.coerceIn(horizontalBounds.min, horizontalBounds.max)
        } else {
            targetX.coerceIn(minScrollX(), maxScrollX())
        }
        val correctedY = if (verticalBounds != null) {
            targetY.coerceIn(verticalBounds.min, verticalBounds.max)
        } else {
            targetY.coerceIn(minScrollY(), maxScrollY())
        }
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
        clampScrollOffsetsToVisibleFrame()

        detachAndScrapAttachedViews(recycler)
        fillVisibleChildren(recycler, state)
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        if (itemCount <= 0) return

        val isPrepend = positionStart == 0
        val isAppend = positionStart >= knownItemCount
        val direction = if (isPrepend) pendingPrependDirection else pendingAppendDirection
        val anchor = captureAnchor(if (isPrepend) itemCount else 0)

        if (spanFrames.isEmpty() || knownItemCount == 0 || (!isPrepend && !isAppend)) {
            growContentBounds(direction, itemCount, isPrepend)
            rebuildFrames(knownItemCount + itemCount)
        } else if (isPrepend) {
            shiftFramesForInsert(positionStart, itemCount)
            layoutPrependedFrames(direction, itemCount)
            knownItemCount += itemCount
        } else {
            layoutAppendedFrames(direction, positionStart, itemCount)
            knownItemCount += itemCount
        }

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
        markOccupiedBoundsDirty()
    }

    private fun shiftFramesForInsert(positionStart: Int, itemCount: Int) {
        if (itemCount <= 0) return

        for (position in knownItemCount - 1 downTo positionStart) {
            val frame = spanFrames.remove(position) ?: continue
            spanFrames[position + itemCount] = frame
        }
        markOccupiedBoundsDirty()
    }

    private fun layoutPrependedFrames(direction: InsertDirection, itemCount: Int) {
        ensureInitialContentBounds()
        var positions = (0 until itemCount).toMutableList()
        positions = layoutPendingBlankFillFrames(positions)
        if (positions.isEmpty()) return

        when (direction) {
            InsertDirection.LEFT -> {
                val oldLeft = contentBounds.left
                val newLeft = oldLeft - pageSpans
                val insertTop = horizontalInsertTop()
                val layoutArea = Rect(newLeft, insertTop, oldLeft, Int.MAX_VALUE)
                val bottom = layoutPositionsInArea(positions, layoutArea)

                contentBounds.left = newLeft
                contentBounds.top = min(contentBounds.top, insertTop)
                contentBounds.bottom = max(contentBounds.bottom, bottom)
            }

            InsertDirection.UP,
            InsertDirection.DOWN -> {
                val layoutArea = verticalInsertArea(contentBounds.top)
                val height = measureRowsForPositions(positions, layoutArea.width())
                val oldTop = contentBounds.top
                layoutArea.top = oldTop - height
                layoutPositionsInArea(positions, layoutArea)

                contentBounds.left = min(contentBounds.left, layoutArea.left)
                contentBounds.right = max(contentBounds.right, layoutArea.right)
                contentBounds.top = oldTop - height
            }

            InsertDirection.RIGHT -> {
                layoutAppendedPositions(InsertDirection.RIGHT, positions)
            }
        }
    }

    private fun layoutAppendedFrames(direction: InsertDirection, positionStart: Int, itemCount: Int) {
        ensureInitialContentBounds()
        var positions = (positionStart until positionStart + itemCount).toMutableList()
        positions = layoutPendingBlankFillFrames(positions)
        if (positions.isEmpty()) return

        layoutAppendedPositions(direction, positions)
    }

    private fun layoutAppendedPositions(direction: InsertDirection, positions: List<Int>) {
        when (direction) {
            InsertDirection.RIGHT -> {
                val oldRight = contentBounds.right
                val newRight = oldRight + pageSpans
                val insertTop = horizontalInsertTop()
                val layoutArea = Rect(oldRight, insertTop, newRight, Int.MAX_VALUE)
                val bottom = layoutPositionsInArea(positions, layoutArea)

                contentBounds.right = newRight
                contentBounds.top = min(contentBounds.top, insertTop)
                contentBounds.bottom = max(contentBounds.bottom, bottom)
            }

            InsertDirection.LEFT -> {
                val oldLeft = contentBounds.left
                val newLeft = oldLeft - pageSpans
                val insertTop = horizontalInsertTop()
                val layoutArea = Rect(newLeft, insertTop, oldLeft, Int.MAX_VALUE)
                val bottom = layoutPositionsInArea(positions, layoutArea)

                contentBounds.left = newLeft
                contentBounds.top = min(contentBounds.top, insertTop)
                contentBounds.bottom = max(contentBounds.bottom, bottom)
            }

            InsertDirection.UP -> {
                val layoutArea = verticalInsertArea(contentBounds.top)
                val height = measureRowsForPositions(positions, layoutArea.width())
                val oldTop = contentBounds.top
                layoutArea.top = oldTop - height
                layoutPositionsInArea(positions, layoutArea)

                contentBounds.left = min(contentBounds.left, layoutArea.left)
                contentBounds.right = max(contentBounds.right, layoutArea.right)
                contentBounds.top = oldTop - height
            }

            InsertDirection.DOWN -> {
                val layoutArea = verticalInsertArea(contentBounds.bottom)
                contentBounds.bottom = layoutPositionsInArea(positions, layoutArea)
                contentBounds.left = min(contentBounds.left, layoutArea.left)
                contentBounds.right = max(contentBounds.right, layoutArea.right)
            }
        }
    }

    private fun layoutPendingBlankFillFrames(positions: MutableList<Int>): MutableList<Int> {
        if (positions.isEmpty() || pendingBlankFillAreas.isEmpty()) {
            return positions
        }

        val blankAreas = pendingBlankFillAreas.toList()
        pendingBlankFillAreas.clear()
        var remainingPositions = positions

        for (blankArea in blankAreas) {
            if (remainingPositions.isEmpty()) {
                break
            }
            if (blankArea.width() <= 0 || blankArea.height() <= 0 || hasFrameIntersectingSpanRect(blankArea)) {
                continue
            }

            val beforeCount = remainingPositions.size
            remainingPositions = layoutPositionsInAreaUntilFull(remainingPositions, blankArea)
            if (remainingPositions.size < beforeCount) {
                contentBounds.union(blankArea)
                markOccupiedBoundsDirty()
            }
        }
        return remainingPositions
    }

    private fun layoutFramesInArea(positionStart: Int, itemCount: Int, layoutArea: Rect): Int {
        return layoutPositionsInArea((positionStart until positionStart + itemCount).toList(), layoutArea)
    }

    private fun layoutPositionsInArea(positions: List<Int>, layoutArea: Rect): Int {
        val rectsHelper = TwoWayRectsHelper(layoutArea)
        var bottom = layoutArea.top

        for (position in positions) {
            val spanSize = spanSizeLookup?.getSpanSize(position) ?: SpanSize(1, 1)
            validateSpanSize(spanSize, layoutArea.width())

            val spanRect = rectsHelper.findRect(spanSize)
            rectsHelper.pushRect(spanRect)
            spanFrames[position] = spanRect
            bottom = max(bottom, spanRect.bottom)
        }

        markOccupiedBoundsDirty()
        return bottom
    }

    private fun layoutPositionsInAreaUntilFull(positions: List<Int>, layoutArea: Rect): MutableList<Int> {
        val rectsHelper = TwoWayRectsHelper(layoutArea)
        val remainingPositions = mutableListOf<Int>()

        for (position in positions) {
            val spanSize = spanSizeLookup?.getSpanSize(position) ?: SpanSize(1, 1)
            if (spanSize.width < 1 || spanSize.width > layoutArea.width() || spanSize.height > layoutArea.height()) {
                remainingPositions.add(position)
                continue
            }

            val spanRect = rectsHelper.findRectOrNull(spanSize)
            if (spanRect == null) {
                remainingPositions.add(position)
            } else {
                rectsHelper.pushRect(spanRect)
                spanFrames[position] = spanRect
            }
        }

        markOccupiedBoundsDirty()
        return remainingPositions
    }

    private fun measureRowsForItems(positionStart: Int, itemCount: Int, widthSpans: Int): Int {
        return measureRowsForPositions((positionStart until positionStart + itemCount).toList(), widthSpans)
    }

    private fun measureRowsForPositions(positions: List<Int>, widthSpans: Int): Int {
        val layoutArea = Rect(0, 0, max(1, widthSpans), Int.MAX_VALUE)
        val rectsHelper = TwoWayRectsHelper(layoutArea)
        var bottom = layoutArea.top

        for (position in positions) {
            val spanSize = spanSizeLookup?.getSpanSize(position) ?: SpanSize(1, 1)
            validateSpanSize(spanSize, layoutArea.width())

            val spanRect = rectsHelper.findRect(spanSize)
            rectsHelper.pushRect(spanRect)
            bottom = max(bottom, spanRect.bottom)
        }

        return max(1, bottom - layoutArea.top)
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
        occupiedBounds.set(0, 0, 0, 0)
        markOccupiedBoundsDirty()
        knownItemCount = 0
        pendingScrollToPosition = null
        pendingAppendDirection = InsertDirection.DOWN
        pendingPrependDirection = InsertDirection.UP
        pendingBlankFillAreas.clear()

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

    private fun clampScrollOffsetsToVisibleFrame() {
        if (spanFrames.isEmpty() || itemSize <= 0 || hasVisibleFrameAt(scrollX, scrollY)) {
            return
        }

        val nearestOffset = findNearestVisibleScrollOffset(scrollX, scrollY) ?: return
        scrollX = nearestOffset.x
        scrollY = nearestOffset.y
    }

    private fun fillVisibleChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        recycleInvisibleChildren(recycler)
        fillVisibleChildrenInViewport(recycler, state)

        if (state.itemCount > 0 && spanFrames.isNotEmpty() && !hasVisibleFrameAt(scrollX, scrollY)) {
            val nearestOffset = findNearestVisibleScrollOffset(scrollX, scrollY) ?: return
            if (nearestOffset.x != scrollX || nearestOffset.y != scrollY) {
                scrollX = nearestOffset.x
                scrollY = nearestOffset.y
                recycleInvisibleChildren(recycler)
                fillVisibleChildrenInViewport(recycler, state)
            }
        }
    }

    private fun fillVisibleChildrenInViewport(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
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
        val travelled = findAllowedTravel(delta, axis)
        if (travelled == 0) {
            return 0
        }

        if (axis == Axis.HORIZONTAL) {
            scrollX += travelled
        } else {
            scrollY += travelled
        }

        return travelled
    }

    private fun findAllowedTravel(delta: Int, axis: Axis): Int {
        if (delta == 0) return 0

        val sliceBounds = visibleSliceScrollBounds(axis)
        if (sliceBounds == null) {
            notifyScrollBlocked(axis, delta)
            return 0
        }

        val currentOffset = if (axis == Axis.HORIZONTAL) scrollX else scrollY
        val requestedTravel = travelWithinBounds(currentOffset, delta, sliceBounds)
        if (requestedTravel == 0) {
            notifyScrollBlocked(axis, delta)
            return 0
        }

        val targetX = if (axis == Axis.HORIZONTAL) scrollX + requestedTravel else scrollX
        val targetY = if (axis == Axis.VERTICAL) scrollY + requestedTravel else scrollY
        val targetHasContent = hasVisibleFrameAt(targetX, targetY)
        if (!targetHasContent || requestedTravel != delta) {
            notifyScrollBlocked(axis, delta)
        }
        return if (targetHasContent) requestedTravel else 0
    }

    private fun travelWithinBounds(currentOffset: Int, delta: Int, bounds: ScrollBounds): Int {
        val targetOffset = currentOffset + delta

        return when {
            currentOffset < bounds.min -> {
                if (delta <= 0) 0 else min(targetOffset, bounds.max) - currentOffset
            }

            currentOffset > bounds.max -> {
                if (delta >= 0) 0 else max(targetOffset, bounds.min) - currentOffset
            }

            else -> targetOffset.coerceIn(bounds.min, bounds.max) - currentOffset
        }
    }

    private fun notifyScrollBlocked(axis: Axis, delta: Int) {
        val direction = when (axis) {
            Axis.HORIZONTAL -> if (delta > 0) InsertDirection.RIGHT else InsertDirection.LEFT
            Axis.VERTICAL -> if (delta > 0) InsertDirection.DOWN else InsertDirection.UP
        }
        onScrollBlocked?.invoke(direction)
    }

    private fun hasVisibleFrameAt(candidateScrollX: Int, candidateScrollY: Int): Boolean {
        if (horizontalSpace <= 0 || verticalSpace <= 0) return true

        val viewport = Rect(
            candidateScrollX,
            candidateScrollY,
            candidateScrollX + horizontalSpace,
            candidateScrollY + verticalSpace
        )

        for (frame in spanFrames.values) {
            if (viewport.intersects(frame.toPixelRect())) {
                return true
            }
        }

        return false
    }

    private fun hasFrameIntersectingPixelRect(rect: Rect): Boolean {
        for (frame in spanFrames.values) {
            if (rect.intersects(frame.toPixelRect())) {
                return true
            }
        }
        return false
    }

    private fun hasFrameIntersectingSpanRect(rect: Rect): Boolean {
        for (frame in spanFrames.values) {
            if (rect.intersects(frame)) {
                return true
            }
        }
        return false
    }

    private fun findNearestVisibleScrollOffset(candidateScrollX: Int, candidateScrollY: Int): ScrollOffset? {
        if (horizontalSpace <= 0 || verticalSpace <= 0) return null

        var nearestOffset: ScrollOffset? = null
        var nearestDistance = Long.MAX_VALUE

        for (frame in spanFrames.values) {
            val pixelFrame = frame.toPixelRect()
            val targetX = nearestViewportOffsetForFrame(
                frameStart = pixelFrame.left,
                frameEnd = pixelFrame.right,
                viewportStart = candidateScrollX,
                viewportSize = horizontalSpace,
                minOffset = minScrollX(),
                maxOffset = maxScrollX()
            )
            val targetY = nearestViewportOffsetForFrame(
                frameStart = pixelFrame.top,
                frameEnd = pixelFrame.bottom,
                viewportStart = candidateScrollY,
                viewportSize = verticalSpace,
                minOffset = minScrollY(),
                maxOffset = maxScrollY()
            )
            val distanceX = targetX - candidateScrollX
            val distanceY = targetY - candidateScrollY
            val distance = distanceX.toLong() * distanceX + distanceY.toLong() * distanceY

            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestOffset = ScrollOffset(targetX, targetY)
            }
        }

        return nearestOffset
    }

    private fun visibleSliceScrollBounds(axis: Axis): ScrollBounds? {
        val edges = visibleSliceContentEdges(axis) ?: return null
        val maxOffset = if (axis == Axis.HORIZONTAL) {
            edges.max - horizontalSpace
        } else {
            edges.max - verticalSpace
        }
        val normalizedMax = max(edges.min, maxOffset)
        return ScrollBounds(edges.min, normalizedMax)
    }

    private fun visibleSliceContentEdges(axis: Axis): ContentEdges? {
        if (horizontalSpace <= 0 || verticalSpace <= 0 || spanFrames.isEmpty()) return null
        val orthogonalStart = if (axis == Axis.HORIZONTAL) scrollY else scrollX
        val orthogonalEnd = if (axis == Axis.HORIZONTAL) scrollY + verticalSpace else scrollX + horizontalSpace
        var minEdge = Int.MAX_VALUE
        var maxEdge = Int.MIN_VALUE

        for (frame in spanFrames.values) {
            val pixelFrame = frame.toPixelRect()
            val intersectsSlice = if (axis == Axis.HORIZONTAL) {
                pixelFrame.bottom > orthogonalStart && pixelFrame.top < orthogonalEnd
            } else {
                pixelFrame.right > orthogonalStart && pixelFrame.left < orthogonalEnd
            }
            if (!intersectsSlice) continue

            if (axis == Axis.HORIZONTAL) {
                minEdge = min(minEdge, pixelFrame.left)
                maxEdge = max(maxEdge, pixelFrame.right)
            } else {
                minEdge = min(minEdge, pixelFrame.top)
                maxEdge = max(maxEdge, pixelFrame.bottom)
            }
        }

        if (minEdge == Int.MAX_VALUE) return null
        return ContentEdges(minEdge, maxEdge)
    }

    private fun prepareVisibleBlankFillAreas() {
        pendingBlankFillAreas.clear()
        val viewport = visibleSpanRect() ?: return
        pendingBlankFillAreas.addAll(findVisibleBlankSpanAreas(viewport))
    }

    private fun horizontalInsertTop(): Int {
        return visibleSpanRect()?.top ?: contentBounds.top
    }

    private fun verticalInsertArea(top: Int): Rect {
        val viewport = visibleSpanRect()
        val left = viewport?.left ?: contentBounds.left
        val right = max(left + visibleSpans, viewport?.right ?: contentBounds.right)
        return Rect(left, top, right, Int.MAX_VALUE)
    }

    private fun findVisibleBlankSpanAreas(viewport: Rect): List<Rect> {
        val blankAreas = mutableListOf<Rect>()

        for (row in viewport.top until viewport.bottom) {
            var runStart: Int? = null

            for (column in viewport.left until viewport.right) {
                val cell = Rect(column, row, column + 1, row + 1)
                val isBlank = !hasFrameIntersectingSpanRect(cell)
                if (isBlank) {
                    if (runStart == null) {
                        runStart = column
                    }
                } else {
                    appendBlankRun(blankAreas, runStart, column, row)
                    runStart = null
                }
            }

            appendBlankRun(blankAreas, runStart, viewport.right, row)
        }

        return blankAreas.sortedWith(
            compareBy<Rect> { it.top }
                .thenBy { it.left }
                .thenByDescending { it.width() * it.height() }
        )
    }

    private fun appendBlankRun(blankAreas: MutableList<Rect>, runStart: Int?, runEnd: Int, row: Int) {
        val start = runStart ?: return
        if (runEnd <= start) return

        val existing = blankAreas.asReversed().firstOrNull {
            it.left == start && it.right == runEnd && it.bottom == row
        }
        if (existing != null) {
            existing.bottom = row + 1
        } else {
            blankAreas.add(Rect(start, row, runEnd, row + 1))
        }
    }

    private fun visibleSpanRect(): Rect? {
        if (itemSize <= 0 || horizontalSpace <= 0 || verticalSpace <= 0) return null

        return Rect(
            floorSpan(scrollX),
            floorSpan(scrollY),
            ceilSpan(scrollX + horizontalSpace),
            ceilSpan(scrollY + verticalSpace)
        )
    }

    private fun findLargestBlankRowArea(viewport: Rect): Rect? {
        var currentStart: Int? = null
        var bestStart = 0
        var bestEnd = 0

        for (row in viewport.top until viewport.bottom) {
            val rowRect = Rect(viewport.left, row, viewport.right, row + 1)
            val isBlank = !hasFrameIntersectingSpanRect(rowRect)
            if (isBlank) {
                if (currentStart == null) {
                    currentStart = row
                }
            } else {
                val start = currentStart
                if (start != null && row - start > bestEnd - bestStart) {
                    bestStart = start
                    bestEnd = row
                }
                currentStart = null
            }
        }

        val start = currentStart
        if (start != null && viewport.bottom - start > bestEnd - bestStart) {
            bestStart = start
            bestEnd = viewport.bottom
        }

        return if (bestEnd > bestStart) {
            Rect(viewport.left, bestStart, viewport.right, bestEnd)
        } else {
            null
        }
    }

    private fun findLargestBlankColumnArea(viewport: Rect): Rect? {
        var currentStart: Int? = null
        var bestStart = 0
        var bestEnd = 0

        for (column in viewport.left until viewport.right) {
            val columnRect = Rect(column, viewport.top, column + 1, viewport.bottom)
            val isBlank = !hasFrameIntersectingSpanRect(columnRect)
            if (isBlank) {
                if (currentStart == null) {
                    currentStart = column
                }
            } else {
                val start = currentStart
                if (start != null && column - start > bestEnd - bestStart) {
                    bestStart = start
                    bestEnd = column
                }
                currentStart = null
            }
        }

        val start = currentStart
        if (start != null && viewport.right - start > bestEnd - bestStart) {
            bestStart = start
            bestEnd = viewport.right
        }

        return if (bestEnd > bestStart) {
            Rect(bestStart, viewport.top, bestEnd, viewport.bottom)
        } else {
            null
        }
    }

    private fun floorSpan(pixel: Int): Int {
        return Math.floorDiv(pixel, itemSize)
    }

    private fun ceilSpan(pixel: Int): Int {
        return -Math.floorDiv(-pixel, itemSize)
    }

    private fun ceilDiv(value: Int, divisor: Int): Int {
        return (value + divisor - 1) / divisor
    }

    private fun nearestViewportOffsetForFrame(
        frameStart: Int,
        frameEnd: Int,
        viewportStart: Int,
        viewportSize: Int,
        minOffset: Int,
        maxOffset: Int
    ): Int {
        val viewportEnd = viewportStart + viewportSize
        val offset = when {
            frameEnd <= viewportStart -> frameEnd - viewportSize
            frameStart >= viewportEnd -> frameStart
            else -> viewportStart
        }

        return offset.coerceIn(minOffset, maxOffset)
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
        get() = max(horizontalSpace, occupiedSpanBounds().width() * itemSize)

    private val contentHeight: Int
        get() = max(verticalSpace, occupiedSpanBounds().height() * itemSize)

    private fun minScrollX(): Int {
        return occupiedSpanBounds().left * itemSize
    }

    private fun maxScrollX(): Int {
        return max(minScrollX(), occupiedSpanBounds().right * itemSize - horizontalSpace)
    }

    private fun minScrollY(): Int {
        return occupiedSpanBounds().top * itemSize
    }

    private fun maxScrollY(): Int {
        return max(minScrollY(), occupiedSpanBounds().bottom * itemSize - verticalSpace)
    }

    private fun occupiedSpanBounds(): Rect {
        if (!occupiedBoundsDirty) {
            return occupiedBounds
        }

        if (spanFrames.isEmpty()) {
            occupiedBounds.set(contentBounds)
            occupiedBoundsDirty = false
            return occupiedBounds
        }

        var isFirstFrame = true
        for (frame in spanFrames.values) {
            if (isFirstFrame) {
                occupiedBounds.set(frame)
                isFirstFrame = false
            } else {
                occupiedBounds.union(frame)
            }
        }

        occupiedBoundsDirty = false
        return occupiedBounds
    }

    private fun markOccupiedBoundsDirty() {
        occupiedBoundsDirty = true
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

    private data class ScrollOffset(
        val x: Int,
        val y: Int
    )

    private data class ScrollBounds(
        val min: Int,
        val max: Int
    )

    private data class ContentEdges(
        val min: Int,
        val max: Int
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
        return findRectOrNull(spanSize)
            ?: throw NoSuchElementException("No free rect can contain span size ${spanSize.width}x${spanSize.height}")
    }

    fun findRectOrNull(spanSize: SpanSize): Rect? {
        val lane = freeRects.firstOrNull {
            val itemRect = Rect(
                it.left,
                it.top,
                it.left + spanSize.width,
                it.top + spanSize.height
            )
            it.contains(itemRect)
        } ?: return null

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
