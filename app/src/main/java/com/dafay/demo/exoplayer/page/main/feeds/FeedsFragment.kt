package com.dafay.demo.exoplayer.page.main.feeds

import android.content.ComponentName
import android.content.Intent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.TwoWaySpannedGridLayoutManager
import com.dafay.demo.data.source.data.Result
import com.dafay.demo.exoplayer.PlaybackService
import com.dafay.demo.exoplayer.databinding.FragmentFeedsBinding
import com.dafay.demo.exoplayer.page.player.NowPlayingActivity
import com.dafay.demo.exoplayer.ui.PlayingBarsDrawable
import com.dafay.demo.lab.base.base.BaseFragment
import com.dafay.demo.lib.base.utils.debug
import com.dafay.demo.lib.base.utils.dp2px
import com.example.demo.biz.base.widgets.GridMarginDecoration
import com.google.common.util.concurrent.ListenableFuture
import com.google.android.material.color.MaterialColors

class FeedsFragment : BaseFragment<FragmentFeedsBinding>(FragmentFeedsBinding::inflate) {

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser? get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

    private lateinit var feedAdapter: FeedAdapter
    private val playingBarsDrawable = PlayingBarsDrawable()

    private lateinit var viewModel: FeedsViewModel
    private lateinit var spannedGridLayoutManager: TwoWaySpannedGridLayoutManager
    private var observerInitialized = false
    private var initialRefreshRequested = false
    private var wasDragging = false
    private var playerListenerAdded = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNowPlayingFab(isPlaying)
        }
    }

    override fun onStart() {
        super.onStart()
        if (::viewModel.isInitialized) {
            delayInitObserver()
            browser?.let {
                attachPlayerListener(it)
            }
        } else {
            initializeBrowser()
        }
    }

    override fun initViews() {
        super.initViews()
        initRecyclerView()
    }

    private fun initRecyclerView() {
        feedAdapter = FeedAdapter()
        spannedGridLayoutManager = TwoWaySpannedGridLayoutManager(
            visibleSpans = 4,
            contentSpans = 8,
            pageSpans = 4
        )
        initNowPlayingFab()
        binding.rvRecyclerview.addItemDecoration(GridMarginDecoration(4.dp2px, 4.dp2px, 4.dp2px, 4.dp2px))
        binding.rvRecyclerview.layoutManager = spannedGridLayoutManager
        binding.rvRecyclerview.adapter = feedAdapter
        spannedGridLayoutManager.onScrollBlocked = { direction ->
            if (::viewModel.isInitialized && feedAdapter.datas.isNotEmpty()) {
                viewModel.loadMore(direction.toFeedLoadDirection())
            }
        }

        spannedGridLayoutManager.spanSizeLookup = TwoWaySpannedGridLayoutManager.SpanSizeLookup { position ->
            when (position % 4) {
                0 -> {
                    SpanSize(2, 2)
                }

                else -> {
                    SpanSize(1, 1)
                }
            }
        }

        binding.rvRecyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!::viewModel.isInitialized || feedAdapter.datas.isEmpty()) {
                    return
                }

                if (dx > 0 && spannedGridLayoutManager.isNearRightEdge()) {
                    viewModel.loadMore(FeedLoadDirection.RIGHT)
                } else if (dx < 0 && spannedGridLayoutManager.isNearLeftEdge()) {
                    viewModel.loadMore(FeedLoadDirection.LEFT)
                }

                if (dy > 0 && spannedGridLayoutManager.isNearBottomEdge()) {
                    viewModel.loadMore(FeedLoadDirection.DOWN)
                } else if (dy < 0 && spannedGridLayoutManager.isNearTopEdge()) {
                    viewModel.loadMore(FeedLoadDirection.UP)
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        wasDragging = true
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        spannedGridLayoutManager.snapToNearestSpan(recyclerView)
                        spannedGridLayoutManager.clearDragAxis()

                        if (wasDragging && ::viewModel.isInitialized && feedAdapter.datas.isNotEmpty()) {
                            viewModel.prefetchAround(FeedsViewModel.DEFAULT_PREFETCH_PAGE_COUNT)
                        }
                        wasDragging = false
                    }
                }
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

    private fun initNowPlayingFab() {
        val iconColor = MaterialColors.getColor(
            binding.fabNowPlaying,
            com.google.android.material.R.attr.colorOnPrimary
        )
        playingBarsDrawable.setBarColor(iconColor)
        binding.fabNowPlaying.setImageDrawable(playingBarsDrawable)
        binding.fabNowPlaying.hide()
        binding.fabNowPlaying.setOnClickListener {
            startActivity(Intent(requireContext(), NowPlayingActivity::class.java))
        }
    }

    private fun initializeBrowser() {
        if (::browserFuture.isInitialized) {
            return
        }

        browserFuture = MediaBrowser.Builder(
            requireContext(),
            SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        ).buildAsync()
        browserFuture.addListener({
            debug("initializeBrowser success")
            val mediaBrowser = browser ?: return@addListener
            viewModel = FeedsViewModel(mediaBrowser)
            attachPlayerListener(mediaBrowser)
            delayInitObserver()
            refreshInitialDataIfNeeded()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun attachPlayerListener(mediaBrowser: MediaBrowser) {
        if (!playerListenerAdded) {
            mediaBrowser.addListener(playerListener)
            playerListenerAdded = true
        }
        updateNowPlayingFab(mediaBrowser.isPlaying)
    }

    private fun updateNowPlayingFab(isPlaying: Boolean) {
        if (view == null) return

        if (isPlaying) {
            if (!playingBarsDrawable.isRunning) {
                playingBarsDrawable.start()
            }
            binding.fabNowPlaying.show()
        } else {
            binding.fabNowPlaying.hide()
            playingBarsDrawable.stop()
        }
    }

    private fun delayInitObserver() {
        if (observerInitialized) {
            return
        }
        observerInitialized = true

        viewModel.feedPageLiveData.observe(viewLifecycleOwner) {
            when (val result = it.result) {
                is Result.Loading -> {}
                is Result.Success -> {
                    applyPage(it.direction, result.value)
                }
                else -> {}
            }
        }
    }

    private fun applyPage(direction: FeedLoadDirection, mediaItems: List<MediaItem>) {
        when (direction) {
            FeedLoadDirection.INITIAL -> {
                feedAdapter.setDatas(mediaItems)
                binding.rvRecyclerview.doOnNextLayout {
                    viewModel.prefetchAround(FeedsViewModel.DEFAULT_PREFETCH_PAGE_COUNT)
                }
            }

            FeedLoadDirection.LEFT,
            FeedLoadDirection.UP -> {
                spannedGridLayoutManager.prepareForPrepend(direction.toInsertDirection())
                feedAdapter.prependDatas(mediaItems)
            }

            FeedLoadDirection.RIGHT,
            FeedLoadDirection.DOWN -> {
                spannedGridLayoutManager.prepareForAppend(direction.toInsertDirection())
                feedAdapter.addDatas(mediaItems)
            }
        }
    }

    private fun FeedLoadDirection.toInsertDirection(): TwoWaySpannedGridLayoutManager.InsertDirection {
        return when (this) {
            FeedLoadDirection.LEFT -> TwoWaySpannedGridLayoutManager.InsertDirection.LEFT
            FeedLoadDirection.UP -> TwoWaySpannedGridLayoutManager.InsertDirection.UP
            FeedLoadDirection.RIGHT -> TwoWaySpannedGridLayoutManager.InsertDirection.RIGHT
            FeedLoadDirection.DOWN,
            FeedLoadDirection.INITIAL -> TwoWaySpannedGridLayoutManager.InsertDirection.DOWN
        }
    }

    private fun TwoWaySpannedGridLayoutManager.InsertDirection.toFeedLoadDirection(): FeedLoadDirection {
        return when (this) {
            TwoWaySpannedGridLayoutManager.InsertDirection.LEFT -> FeedLoadDirection.LEFT
            TwoWaySpannedGridLayoutManager.InsertDirection.UP -> FeedLoadDirection.UP
            TwoWaySpannedGridLayoutManager.InsertDirection.RIGHT -> FeedLoadDirection.RIGHT
            TwoWaySpannedGridLayoutManager.InsertDirection.DOWN -> FeedLoadDirection.DOWN
        }
    }

    private fun refreshInitialDataIfNeeded() {
        if (initialRefreshRequested || feedAdapter.datas.isNotEmpty()) {
            return
        }

        initialRefreshRequested = true
        viewModel.refresh()
    }

    override fun onDestroy() {
        if (::viewModel.isInitialized) {
            viewModel.release()
        }
        if (::browserFuture.isInitialized) {
            MediaBrowser.releaseFuture(browserFuture)
        }
        super.onDestroy()
    }

    override fun onDestroyView() {
        if (::spannedGridLayoutManager.isInitialized) {
            spannedGridLayoutManager.onScrollBlocked = null
        }
        browser?.removeListener(playerListener)
        playerListenerAdded = false
        playingBarsDrawable.stop()
        observerInitialized = false
        super.onDestroyView()
    }
}
