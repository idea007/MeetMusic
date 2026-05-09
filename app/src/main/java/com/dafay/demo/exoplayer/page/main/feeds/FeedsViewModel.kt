package com.dafay.demo.exoplayer.page.main.feeds

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import com.dafay.demo.data.source.data.Result
import com.dafay.demo.data.source.data.model.PAGE
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.random.Random

enum class FeedLoadDirection {
    INITIAL, LEFT, UP, RIGHT, DOWN
}

data class FeedPageEvent(
    val direction: FeedLoadDirection,
    val result: Result<List<MediaItem>>
)

class FeedsViewModel(private val browser: MediaBrowser) : ViewModel() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queryExecutor = Executors.newSingleThreadExecutor()
    private val cachedPages = ArrayDeque<List<MediaItem>>()
    private val pendingDirections = ArrayDeque<FeedLoadDirection>()
    private val applyingDirections = mutableSetOf<FeedLoadDirection>()
    private var activeCacheRequests = 0
    private var initialLoading = false
    private var nextCachePage = 0
    private var queryGeneration = 0
    private val requestedInitialPages = mutableSetOf<Int>()

    val feedPageLiveData = MutableLiveData<FeedPageEvent>()

    fun refresh() {
        if (initialLoading) {
            return
        }

        initialLoading = true
        queryGeneration++
        cachedPages.clear()
        pendingDirections.clear()
        applyingDirections.clear()
        requestedInitialPages.clear()
        activeCacheRequests = 0
        requestInitialPage(Random.nextInt(0, INITIAL_RANDOM_PAGE_BOUND))
    }

    fun loadMore(direction: FeedLoadDirection) {
        if (direction == FeedLoadDirection.INITIAL || initialLoading) {
            return
        }
        if (direction in applyingDirections || direction in pendingDirections) {
            return
        }

        val cachedPage = cachedPages.pollFirst()
        if (cachedPage != null) {
            applyingDirections.add(direction)
            replaceConsumedCachePage()
            feedPageLiveData.value = FeedPageEvent(direction, Result.Success(cachedPage))
            return
        }

        pendingDirections.add(direction)
        ensureCacheFilled(DEFAULT_PREFETCH_PAGE_COUNT)
    }

    fun prefetchAround(pageCount: Int = DEFAULT_PREFETCH_PAGE_COUNT) {
        ensureCacheFilled(pageCount)
    }

    fun onPageApplied(direction: FeedLoadDirection) {
        if (direction != FeedLoadDirection.INITIAL) {
            applyingDirections.remove(direction)
        }
    }

    private fun ensureCacheFilled(targetCount: Int) {
        if (initialLoading || targetCount <= 0) {
            return
        }

        val missingCount = targetCount - cachedPages.size - activeCacheRequests
        repeat(max(0, missingCount)) {
            requestCachePage()
        }
    }

    private fun replaceConsumedCachePage() {
        if (initialLoading) {
            return
        }

        requestCachePage()
    }

    private fun requestCachePage() {
        activeCacheRequests++
        query(nextCachePage++, QueryTarget.CACHE, queryGeneration)
    }

    private fun requestInitialPage(page: Int) {
        requestedInitialPages.add(page)
        nextCachePage = page + 1
        query(page, QueryTarget.INITIAL, queryGeneration)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun query(page: Int, target: QueryTarget, generation: Int) {
        if (target == QueryTarget.INITIAL) {
            feedPageLiveData.postValue(FeedPageEvent(FeedLoadDirection.INITIAL, Result.Loading))
        }

        val pageSize = PAGE.PAGE_SIZE_THIRTY
        val params = MediaLibraryService.LibraryParams.Builder().setExtras(Bundle().apply {
            putString("des", "get album songs")
            putString("action", "feed")
            putInt("offset", page * pageSize)
            putInt("limit", pageSize)
        }).build()
        val childrenFuture =
            browser.getChildren(
                "root",
                page,
                pageSize,
                params
            )

        childrenFuture.addListener(
            {
                val result = try {
                    childrenFuture.get().value
                } catch (e: Exception) {
                    null
                }

                mainHandler.post {
                    if (generation != queryGeneration) {
                        return@post
                    }

                    handleQueryResult(target, result)
                }
            },
            queryExecutor
        )
    }

    private fun handleQueryResult(target: QueryTarget, children: List<MediaItem>?) {
        when (target) {
            QueryTarget.INITIAL -> {
                if (children.isNullOrEmpty()) {
                    val fallbackPage = findInitialFallbackPage()
                    if (fallbackPage != null) {
                        requestInitialPage(fallbackPage)
                        return
                    }

                    initialLoading = false
                    feedPageLiveData.value = FeedPageEvent(FeedLoadDirection.INITIAL, Result.Error(201, "无数据"))
                } else {
                    initialLoading = false
                    feedPageLiveData.value = FeedPageEvent(FeedLoadDirection.INITIAL, Result.Success(children))
                    ensureCacheFilled(DEFAULT_PREFETCH_PAGE_COUNT)
                }
            }

            QueryTarget.CACHE -> {
                activeCacheRequests = max(0, activeCacheRequests - 1)
                if (children.isNullOrEmpty()) {
                    failPendingDirection()
                    clearPendingDirectionsIfCacheDrained()
                    return
                }

                val direction = pendingDirections.pollFirst()
                if (direction != null) {
                    applyingDirections.add(direction)
                    replaceConsumedCachePage()
                    feedPageLiveData.value = FeedPageEvent(direction, Result.Success(children))
                } else {
                    cachedPages.addLast(children)
                }

                ensureCacheFilled(DEFAULT_PREFETCH_PAGE_COUNT)
            }
        }
    }

    private fun findInitialFallbackPage(): Int? {
        return (0 until INITIAL_FALLBACK_PAGE_COUNT).firstOrNull { page ->
            page !in requestedInitialPages
        }
    }

    private fun failPendingDirection() {
        val failedDirection = pendingDirections.pollFirst() ?: return
        feedPageLiveData.value = FeedPageEvent(failedDirection, Result.Error(201, "无数据"))
    }

    private fun clearPendingDirectionsIfCacheDrained() {
        if (activeCacheRequests > 0 || cachedPages.isNotEmpty()) {
            return
        }

        pendingDirections.clear()
    }

    fun release() {
        queryExecutor.shutdownNow()
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }

    private enum class QueryTarget {
        INITIAL, CACHE
    }

    companion object {
        const val DEFAULT_PREFETCH_PAGE_COUNT = 12
        private const val INITIAL_RANDOM_PAGE_BOUND = 20
        private const val INITIAL_FALLBACK_PAGE_COUNT = 5
    }
}
