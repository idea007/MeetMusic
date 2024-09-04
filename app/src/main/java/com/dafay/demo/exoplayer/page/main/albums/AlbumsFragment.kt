package com.example.demo.wander.music.home.albums

import android.annotation.SuppressLint
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.dafay.demo.exoplayer.page.list.PlayableFolderActivity
import com.dafay.demo.exoplayer.PlaybackService
import com.dafay.demo.exoplayer.databinding.FragmentAlbumsBinding
import com.dafay.demo.lab.base.base.BaseFragment
import com.dafay.demo.lib.base.utils.debug
import com.dafay.demo.lib.base.utils.dp2px
import com.example.demo.biz.base.widgets.GridMarginDecoration
import com.google.common.util.concurrent.ListenableFuture

class AlbumsFragment : BaseFragment<FragmentAlbumsBinding>(FragmentAlbumsBinding::inflate) {

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser? get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

    private lateinit var albumAdapter: AlbumAdapter

    override fun onStart() {
        super.onStart()
        initializeBrowser()
    }

    private fun initializeBrowser() {
        browserFuture = MediaBrowser.Builder(requireContext(), SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))).buildAsync()
        browserFuture.addListener({
            debug("initializeBrowser success")

            initializeAlbums()

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializeAlbums() {
        val browser = this.browser ?: return
        var params = MediaLibraryService.LibraryParams.Builder()
            .setExtras(Bundle().apply {
                putString("des", "get album songs")
                putString("action", "album")
            }).build()
        val childrenFuture =
            browser.getChildren(
                "root",
                0,
                Int.MAX_VALUE,
                params
            )

        childrenFuture.addListener(
            {
                val result = childrenFuture.get()!!
                val children = result.value!!
                albumAdapter.setDatas(children)
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    override fun initViews() {
        super.initViews()
        initRecyclerView()
    }

    private fun initRecyclerView() {
        albumAdapter = AlbumAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecyclerview.addItemDecoration(GridMarginDecoration(16.dp2px, 8.dp2px, 16.dp2px, 8.dp2px))
        binding.rvRecyclerview.layoutManager = layoutManager
        binding.rvRecyclerview.adapter = albumAdapter
        albumAdapter.onItemClickListener=object: AlbumAdapter.AlbumViewHolder.OnItemClickListener{
            override fun onClickItem(view: View, position: Int, mediaItem: MediaItem) {
                if(mediaItem.mediaMetadata.mediaType== MediaMetadata.MEDIA_TYPE_ALBUM){
                    val intent = PlayableFolderActivity.createIntent(requireContext(), mediaItem)
                    startActivity(intent)
                    return
                }

                if (mediaItem.mediaMetadata.isPlayable == true) {
                    val intent = PlayableFolderActivity.createIntent(requireContext(), mediaItem)
                    startActivity(intent)
                }
            }
        }
    }
}