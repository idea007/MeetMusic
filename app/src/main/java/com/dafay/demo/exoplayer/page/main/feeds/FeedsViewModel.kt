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
import com.example.demo.meetsplash.data.model.PAGE
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
    private val lockedDirections = mutableSetOf<FeedLoadDirection>()
    private var activeCacheRequests = 0
    private var initialLoading = false
    private var nextCachePage = 0
    private var queryGeneration = 0

    val feedPageLiveData = MutableLiveData<FeedPageEvent>()

    fun refresh() {
        if (initialLoading) {
            return
        }

        initialLoading = true
        queryGeneration++
        cachedPages.clear()
        pendingDirections.clear()
        lockedDirections.clear()
        activeCacheRequests = 0
        val startPage = Random.nextInt(0, 100) * 100
        nextCachePage = startPage + 1
        query(startPage, QueryTarget.INITIAL, queryGeneration)
    }

    fun loadMore(direction: FeedLoadDirection) {
        if (direction == FeedLoadDirection.INITIAL || initialLoading) {
            return
        }
        if (!lockedDirections.add(direction)) {
            return
        }

        val cachedPage = cachedPages.pollFirst()
        if (cachedPage != null) {
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
            lockedDirections.remove(direction)
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
                initialLoading = false
                if (children.isNullOrEmpty()) {
                    feedPageLiveData.value = FeedPageEvent(FeedLoadDirection.INITIAL, Result.Error(201, "无数据"))
                } else {
                    feedPageLiveData.value = FeedPageEvent(FeedLoadDirection.INITIAL, Result.Success(children))
                    ensureCacheFilled(DEFAULT_PREFETCH_PAGE_COUNT)
                }
            }

            QueryTarget.CACHE -> {
                activeCacheRequests = max(0, activeCacheRequests - 1)
                if (children.isNullOrEmpty()) {
                    val failedDirection = pendingDirections.pollFirst()
                    if (failedDirection != null) {
                        lockedDirections.remove(failedDirection)
                        feedPageLiveData.value = FeedPageEvent(failedDirection, Result.Error(201, "无数据"))
                    }
                    return
                }

                val direction = pendingDirections.pollFirst()
                if (direction != null) {
                    replaceConsumedCachePage()
                    feedPageLiveData.value = FeedPageEvent(direction, Result.Success(children))
                } else {
                    cachedPages.addLast(children)
                }

                ensureCacheFilled(DEFAULT_PREFETCH_PAGE_COUNT)
            }
        }
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
    }
}
