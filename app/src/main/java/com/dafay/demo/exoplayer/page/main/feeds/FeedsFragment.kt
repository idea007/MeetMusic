package com.dafay.demo.exoplayer.page.main.feeds

import android.annotation.SuppressLint
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import com.dafay.demo.exoplayer.PlaybackService
import com.dafay.demo.exoplayer.databinding.FragmentFeedsBinding
import com.dafay.demo.exoplayer.page.list.PlayableFolderActivity
import com.dafay.demo.lab.base.base.BaseFragment
import com.dafay.demo.lib.base.utils.debug
import com.dafay.demo.lib.base.utils.dp2px
import com.example.demo.biz.base.widgets.GridMarginDecoration
import com.google.android.material.color.MaterialColors
import com.google.common.util.concurrent.ListenableFuture
import com.dafay.demo.data.source.data.Result
import com.dafay.demo.lib.base.ui.recy.RecyclerViewInfiniteScrollListener
import com.dafay.demo.lib.base.utils.HandlerUtils

class FeedsFragment : BaseFragment<FragmentFeedsBinding>(FragmentFeedsBinding::inflate) {

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser? get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

    private lateinit var feedAdapter: FeedAdapter

    private lateinit var viewModel: FeedsViewModel

    override fun onStart() {
        super.onStart()
        initializeBrowser()
    }

    override fun initViews() {
        super.initViews()
        initRecyclerView()

        binding.srlRefresh.setProgressBackgroundColorSchemeColor(
            MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnPrimary,
                this::class.java.getCanonicalName()
            )
        )
        binding.srlRefresh.setColorSchemeColors(
            MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorPrimary,
                this::class.java.getCanonicalName()
            )
        )
        binding.srlRefresh.setOnRefreshListener(object : SwipeRefreshLayout.OnRefreshListener {
            override fun onRefresh() {
                if (::viewModel.isInitialized) {
                    viewModel.refresh()
                } else {
                    HandlerUtils.mainHandler.postDelayed({
                        binding.srlRefresh.isRefreshing = false
                    }, 1000)
                }
            }
        })
    }

    private fun initRecyclerView() {
        feedAdapter = FeedAdapter()
        val spannedGridLayoutManager = SpannedGridLayoutManager(orientation = SpannedGridLayoutManager.Orientation.VERTICAL, spans = 4)
        spannedGridLayoutManager.itemOrderIsStable = true
        binding.rvRecyclerview.addItemDecoration(GridMarginDecoration(4.dp2px, 4.dp2px, 4.dp2px, 4.dp2px))
        binding.rvRecyclerview.layoutManager = spannedGridLayoutManager
        binding.rvRecyclerview.adapter = feedAdapter

        spannedGridLayoutManager.spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
            when (position % 4) {
                0 -> {
                    SpanSize(2, 2)
                }

                else -> {
                    SpanSize(1, 1)
                }
            }
        }

        binding.rvRecyclerview.addOnScrollListener(object : RecyclerViewInfiniteScrollListener(spannedGridLayoutManager) {
            override fun onLoadMore() {
                viewModel.loadMore()
            }
        })

        feedAdapter.onItemClickListener = object : FeedAdapter.AlbumViewHolder.OnItemClickListener {
            override fun onClickItem(view: View, position: Int, mediaItem: MediaItem) {

                if (mediaItem.mediaMetadata.isPlayable == true) {

                    run {
                        val browser = browser ?: return@run
                        browser.setMediaItems(
                            feedAdapter.datas,
                            /* startIndex= */ position,
                            /* startPositionMs= */ C.TIME_UNSET
                        )
                        browser.shuffleModeEnabled = false
                        browser.prepare()
                        browser.play()
                        browser.sessionActivity?.send()
                    }

                }
            }
        }
    }

    private fun initializeBrowser() {
        browserFuture = MediaBrowser.Builder(
            requireContext(),
            SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        ).buildAsync()
        browserFuture.addListener({
            debug("initializeBrowser success")
            viewModel = FeedsViewModel(browser!!)
            delayInitObserver()
            viewModel.refresh()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun delayInitObserver() {
        viewModel.refreshMediaItemsLiveData.observe(this) {
            if (!(it is Result.Loading)) {
                binding.srlRefresh.setRefreshing(false)
            }
            when (it) {
                is Result.Loading -> {}
                is Result.Success -> {
                    feedAdapter.setDatas(it.value)
                }
                else -> {}
            }
        }

        viewModel.loadmoreMediaItemsLiveData.observe(this){
            when (it) {
                is Result.Success -> {
                    feedAdapter.addDatas(it.value)
                }
                else -> {}
            }
        }
    }
}