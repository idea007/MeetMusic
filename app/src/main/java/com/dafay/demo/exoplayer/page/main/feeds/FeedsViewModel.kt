package com.dafay.demo.exoplayer.page.main.feeds

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import kotlinx.coroutines.async
import com.dafay.demo.data.source.data.Result
import io.reactivex.Observable
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * @Des
 * @Author m1studio
 * @Date 2024/8/29
 * <a href=" ">相关链接</a>
 */
class FeedsViewModel(private val browser: MediaBrowser) : ViewModel() {
    private var curPageIndex = 1

    val refreshMediaItemsLiveData = MutableLiveData<Result<List<MediaItem>>>()
    val loadmoreMediaItemsLiveData = MutableLiveData<Result<List<MediaItem>>>()

    fun refresh() {
        if (refreshMediaItemsLiveData.value is Result.Loading) {
            return
        }
        refreshMediaItemsLiveData.postValue(Result.Loading)
        curPageIndex = 0
        query(0, 30, refreshMediaItemsLiveData)
    }


    fun loadMore() {
        if (loadmoreMediaItemsLiveData.value is Result.Loading) {
            return
        }
        loadmoreMediaItemsLiveData.postValue(Result.Loading)
        query(curPageIndex, 30, loadmoreMediaItemsLiveData)
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun query(page: Int, pageSize: Int, liveData: MutableLiveData<Result<List<MediaItem>>>) {
        curPageIndex = page
        var params = MediaLibraryService.LibraryParams.Builder().setExtras(Bundle().apply {
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
                val childrens = childrenFuture.get().value
                if (childrens.isNullOrEmpty())
                    liveData.postValue(Result.Error(201, "无数据"))
                else {
                    liveData.postValue(
                        Result.Success(childrens)
                    )
                }
                curPageIndex++
            },
            Executors.newSingleThreadExecutor()
        )
    }

}