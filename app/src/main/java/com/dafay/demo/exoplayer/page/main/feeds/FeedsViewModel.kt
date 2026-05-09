package com.dafay.demo.exoplayer.page.main.feeds

import android.annotation.SuppressLint
import android.os.Bundle
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
    private val queryExecutor = Executors.newSingleThreadExecutor()
    private val loadingDirections = mutableSetOf<FeedLoadDirection>()
    private var nextForwardPage = 0
    private var nextBackwardPage = 0

    val feedPageLiveData = MutableLiveData<FeedPageEvent>()

    fun refresh() {
        if (!loadingDirections.add(FeedLoadDirection.INITIAL)) {
            return
        }

        val startPage = Random.nextInt(0, 100) * 100
        nextForwardPage = startPage + 1
        nextBackwardPage = max(0, startPage - 1)
        query(startPage, FeedLoadDirection.INITIAL)
    }

    fun loadMore(direction: FeedLoadDirection) {
        if (direction == FeedLoadDirection.INITIAL || !loadingDirections.add(direction)) {
            return
        }

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

        val pageSize = PAGE.PAGE_SIZE_FIFTY
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
                try {
                    val children = childrenFuture.get().value
                    if (children.isNullOrEmpty()) {
                        feedPageLiveData.postValue(FeedPageEvent(direction, Result.Error(201, "无数据")))
                    } else {
                        feedPageLiveData.postValue(FeedPageEvent(direction, Result.Success(children)))
                    }
                } catch (e: Exception) {
                    feedPageLiveData.postValue(FeedPageEvent(direction, Result.Error(error = e.message)))
                } finally {
                    loadingDirections.remove(direction)
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
}
