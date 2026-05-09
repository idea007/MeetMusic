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
    private val activeDirections = mutableSetOf<FeedLoadDirection>()
    private val queuedLoads = mutableMapOf<FeedLoadDirection, Int>()
    private var nextForwardPage = 0
    private var nextBackwardPage = 0

    val feedPageLiveData = MutableLiveData<FeedPageEvent>()

    fun refresh() {
        if (!activeDirections.add(FeedLoadDirection.INITIAL)) {
            return
        }

        queuedLoads.clear()
        val startPage = Random.nextInt(0, 100) * 100
        nextForwardPage = startPage + 1
        nextBackwardPage = max(0, startPage - 1)
        query(startPage, FeedLoadDirection.INITIAL)
    }

    fun loadMore(direction: FeedLoadDirection) {
        ensureLoadDepth(direction, 1)
    }

    fun prefetchAround(pageCount: Int = DEFAULT_PREFETCH_PAGE_COUNT) {
        PREFETCH_DIRECTIONS.forEach { direction ->
            ensureLoadDepth(direction, pageCount)
        }
    }

    private fun ensureLoadDepth(direction: FeedLoadDirection, targetCount: Int) {
        if (direction == FeedLoadDirection.INITIAL || targetCount <= 0) {
            return
        }

        val activeCount = if (activeDirections.contains(direction)) 1 else 0
        val queuedCount = queuedLoads[direction] ?: 0
        val missingCount = targetCount - activeCount - queuedCount
        if (missingCount <= 0) {
            return
        }

        queuedLoads[direction] = queuedCount + missingCount
        drainQueuedLoad(direction)
    }

    private fun drainQueuedLoad(direction: FeedLoadDirection) {
        if (activeDirections.contains(direction)) {
            return
        }

        val queuedCount = queuedLoads[direction] ?: 0
        if (queuedCount <= 0) {
            return
        }

        if (queuedCount == 1) {
            queuedLoads.remove(direction)
        } else {
            queuedLoads[direction] = queuedCount - 1
        }

        activeDirections.add(direction)
        val page = when (direction) {
            FeedLoadDirection.LEFT,
            FeedLoadDirection.UP -> takeBackwardPage()
            FeedLoadDirection.RIGHT,
            FeedLoadDirection.DOWN -> nextForwardPage++
            FeedLoadDirection.INITIAL -> nextForwardPage++
        }
        query(page, direction)
    }

    private fun takeBackwardPage(): Int {
        val page = nextBackwardPage
        nextBackwardPage = if (nextBackwardPage > 0) {
            nextBackwardPage - 1
        } else {
            Random.nextInt(0, 100) * 100
        }
        return page
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun query(page: Int, direction: FeedLoadDirection) {
        feedPageLiveData.postValue(FeedPageEvent(direction, Result.Loading))

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
                var shouldDrainQueue = false
                try {
                    val children = childrenFuture.get().value
                    if (children.isNullOrEmpty()) {
                        feedPageLiveData.postValue(FeedPageEvent(direction, Result.Error(201, "无数据")))
                    } else {
                        shouldDrainQueue = true
                        feedPageLiveData.postValue(FeedPageEvent(direction, Result.Success(children)))
                    }
                } catch (e: Exception) {
                    feedPageLiveData.postValue(FeedPageEvent(direction, Result.Error(error = e.message)))
                } finally {
                    mainHandler.post {
                        activeDirections.remove(direction)
                        if (direction != FeedLoadDirection.INITIAL) {
                            if (shouldDrainQueue) {
                                drainQueuedLoad(direction)
                            } else {
                                queuedLoads.remove(direction)
                            }
                        }
                    }
                }
            },
            queryExecutor
        )
    }

    fun release() {
        queryExecutor.shutdownNow()
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }

    companion object {
        const val DEFAULT_PREFETCH_PAGE_COUNT = 3

        private val PREFETCH_DIRECTIONS = listOf(
            FeedLoadDirection.UP,
            FeedLoadDirection.DOWN,
            FeedLoadDirection.LEFT,
            FeedLoadDirection.RIGHT
        )
    }
}
