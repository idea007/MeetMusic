package com.dafay.demo.exoplayer.page.main.feeds

import android.annotation.SuppressLint
import android.content.ComponentName
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.TwoWaySpannedGridLayoutManager
import com.dafay.demo.data.source.data.Result
import com.dafay.demo.exoplayer.PlaybackService
import com.dafay.demo.exoplayer.databinding.FragmentFeedsBinding
import com.dafay.demo.lab.base.base.BaseFragment
import com.dafay.demo.lib.base.utils.debug
import com.dafay.demo.lib.base.utils.dp2px
import com.example.demo.biz.base.widgets.GridMarginDecoration
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs

class FeedsFragment : BaseFragment<FragmentFeedsBinding>(FragmentFeedsBinding::inflate) {

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser? get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

    private lateinit var feedAdapter: FeedAdapter

    private lateinit var viewModel: FeedsViewModel
    private lateinit var spannedGridLayoutManager: TwoWaySpannedGridLayoutManager
    private var observerInitialized = false
    private var initialRefreshRequested = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragAxisLocked = false

    override fun onStart() {
        super.onStart()
        if (::viewModel.isInitialized) {
            delayInitObserver()
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
        binding.rvRecyclerview.addItemDecoration(GridMarginDecoration(4.dp2px, 4.dp2px, 4.dp2px, 4.dp2px))
        binding.rvRecyclerview.layoutManager = spannedGridLayoutManager
        binding.rvRecyclerview.adapter = feedAdapter

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
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    spannedGridLayoutManager.snapToNearestSpan(recyclerView)
                    spannedGridLayoutManager.clearDragAxis()
                }
            }
        })
        bindDragAxisLock()

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

    @SuppressLint("ClickableViewAccessibility")
    private fun bindDragAxisLock() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop

        binding.rvRecyclerview.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    dragAxisLocked = false
                    spannedGridLayoutManager.clearDragAxis()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragAxisLocked) {
                        val dx = event.x - touchDownX
                        val dy = event.y - touchDownY
                        val absDx = abs(dx)
                        val absDy = abs(dy)

                        if (absDx > touchSlop || absDy > touchSlop) {
                            val axis = if (absDx >= absDy) {
                                TwoWaySpannedGridLayoutManager.DragAxis.HORIZONTAL
                            } else {
                                TwoWaySpannedGridLayoutManager.DragAxis.VERTICAL
                            }
                            spannedGridLayoutManager.setDragAxis(axis)
                            dragAxisLocked = true
                        }
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    dragAxisLocked = false
                }
            }

            false
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
            delayInitObserver()
            refreshInitialDataIfNeeded()
        }, ContextCompat.getMainExecutor(requireContext()))
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
                    viewModel.loadMore(FeedLoadDirection.UP)
                    viewModel.loadMore(FeedLoadDirection.LEFT)
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
        observerInitialized = false
        super.onDestroyView()
    }
}
