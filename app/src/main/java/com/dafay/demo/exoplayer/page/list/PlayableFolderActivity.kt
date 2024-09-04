/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dafay.demo.exoplayer.page.list

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.dafay.demo.exoplayer.PlaybackService
import com.dafay.demo.exoplayer.R
import com.dafay.demo.exoplayer.adapter.PlayableMediaItemArrayAdapter
import com.dafay.demo.exoplayer.databinding.ActivityPlayableFolderBinding
import com.dafay.demo.exoplayer.page.player.NowPlayingActivity
import com.dafay.demo.lab.base.base.BaseActivity
import com.dafay.demo.lib.base.utils.debug
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture

class PlayableFolderActivity : BaseActivity<ActivityPlayableFolderBinding>(ActivityPlayableFolderBinding::inflate) {

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser?
        get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

    private lateinit var mediaListAdapter: PlayableMediaItemArrayAdapter

    private lateinit var mediaItem: MediaItem

    companion object {
        private const val MEDIA_ITEM_ID_KEY = "MEDIA_ITEM_ID_KEY"
        private const val MEDIA_ITEM = "MEDIA_ITEM"

        @SuppressLint("UnsafeOptInUsageError")
        fun createIntent(context: Context, mediaItem: MediaItem): Intent {
            val intent = Intent(context, PlayableFolderActivity::class.java)
            intent.putExtra(MEDIA_ITEM_ID_KEY, mediaItem.mediaId)
            intent.putExtra(MEDIA_ITEM, mediaItem.toBundle())
            return intent
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun resolveIntent(intent: Intent?) {
        super.resolveIntent(intent)
        intent?.getBundleExtra(MEDIA_ITEM)?.let {
            mediaItem = MediaItem.fromBundle(it)
        }
    }

    override fun initViews() {
        mediaListAdapter = PlayableMediaItemArrayAdapter()
        binding.rvRecyclerview.layoutManager = LinearLayoutManager(this)
        binding.rvRecyclerview.adapter = mediaListAdapter
        mediaListAdapter.onItemClickListener = object : PlayableMediaItemArrayAdapter.PlayableMediaItemViewHolder.OnItemClickListener {
            override fun onClickAdd(view: View, position: Int, mediaItem: MediaItem) {

                val browser = this@PlayableFolderActivity.browser ?: return
                browser.addMediaItem(mediaItem)
                if (browser.playbackState == Player.STATE_IDLE) {
                    browser.prepare()
                }
                Snackbar.make(
                    findViewById<LinearLayout>(R.id.linear_layout),
                    getString(R.string.added_media_item_format, mediaItem.mediaMetadata.title),
                    BaseTransientBottomBar.LENGTH_SHORT
                ).show()
            }

            override fun onItemClick(view: View, position: Int, mediaItem: MediaItem) {
                run {
                    val browser = browser ?: return@run
                    browser.setMediaItems(
                        mediaListAdapter.datas,
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

        binding.shuffleButton.setOnClickListener {
            val browser = this.browser ?: return@setOnClickListener
            browser.setMediaItems(mediaListAdapter.datas)
            browser.shuffleModeEnabled = true
            browser.prepare()
            browser.play()
//            browser.sessionActivity?.send()

            val intent = Intent(this, NowPlayingActivity::class.java)
            startActivity(intent)
        }

        binding.playButton.setOnClickListener {
            val browser = this.browser ?: return@setOnClickListener
            browser.setMediaItems(mediaListAdapter.datas)
            browser.shuffleModeEnabled = false
            browser.prepare()
            browser.play()
            val intent = Intent(this, NowPlayingActivity::class.java)
            startActivity(intent)
        }

        binding.openPlayerFloatingButton.setOnClickListener {
            browser?.sessionActivity?.send()
        }
    }

    override fun onStart() {
        super.onStart()
        initializeBrowser()
    }

    override fun onStop() {
        super.onStop()
        releaseBrowser()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initializeBrowser() {
        browserFuture =
            MediaBrowser.Builder(
                this,
                SessionToken(this, ComponentName(this, PlaybackService::class.java))
            )
                .buildAsync()
        browserFuture.addListener({ displayFolder() }, ContextCompat.getMainExecutor(this))
    }

    private fun releaseBrowser() {
        MediaBrowser.releaseFuture(browserFuture)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun displayFolder() {
        val browser = this.browser ?: return
//        val id: String = intent.getStringExtra(MEDIA_ITEM_ID_KEY)!!
//        val mediaItemFuture = browser.getItem("[item]jazz_in_paris")
        var params = MediaLibraryService.LibraryParams.Builder()
            .setExtras(Bundle().apply {
                putString("des", "get album songs")
                putString("action", "get_songs")
            }).build()

        val childrenFuture = browser.getChildren(mediaItem.mediaId, /* page= */ 0, /* pageSize= */ Int.MAX_VALUE, /* params= */ params)
//        mediaItemFuture.addListener(
//            {
//                val title: TextView = findViewById(R.id.folder_description)
//                val result = mediaItemFuture.get()!!
//                title.text = result.value!!.mediaMetadata.title
//            },
//            ContextCompat.getMainExecutor(this)
//        )
        childrenFuture.addListener(
            {
                val result = childrenFuture.get()!!
                val children = result.value!!
                debug("children:${children.get(0).mediaMetadata.toBundle().toString()}")
                debug("children:${children.get(0).toBundle().toString()}")
                mediaListAdapter.setDatas(children)
            },
            ContextCompat.getMainExecutor(this)
        )
    }
}
