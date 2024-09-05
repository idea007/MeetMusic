package com.dafay.demo.exoplayer.page.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.media.audiofx.Visualizer
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.dafay.demo.aidl.callback.ReceiveMessageCallback
import com.dafay.demo.aidl.proxy.ExtraSessionServiceProxy
import com.dafay.demo.exoplayer.PlaybackService
import com.dafay.demo.exoplayer.R
import com.dafay.demo.exoplayer.databinding.ActivityNowPlayingBinding
import com.dafay.demo.exoplayer.glide.GlideApp
import com.dafay.demo.exoplayer.page.main.feeds.CROSS_FADE_DURATION
import com.dafay.demo.exoplayer.utils.toPlayerTime
import com.dafay.demo.lab.base.base.BaseActivity
import com.dafay.demo.lib.base.utils.debug
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit


class NowPlayingActivity : BaseActivity<ActivityNowPlayingBinding>(ActivityNowPlayingBinding::inflate) {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone && !controllerFuture.isCancelled) controllerFuture.get() else null

    private var visualizer: Visualizer? = null

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, com.dafay.demo.biz.settings.SettingsActivity::class.java))
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        initializeController()
        ExtraSessionServiceProxy.bindService()
        ExtraSessionServiceProxy.registerReceiverListener(object : ReceiveMessageCallback.Stub() {
            override fun onFFTReady(sampleRateHz: Int, channelCount: Int, fft: FloatArray?) {
                debug("onFFTReady(${sampleRateHz} ${channelCount} ${fft?.size})")
                binding.fftBandView.onFFT(fft ?: floatArrayOf())
            }
        })
    }

    override fun initViews() {
        super.initViews()
        initToolbar()
    }

    private fun updateTrackCover(artworkUri: Uri?) {
        artworkUri ?: return
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(Target.SIZE_ORIGINAL)
            .dontTransform()
        GlideApp.with(binding.ivImg)
            .load(artworkUri)
            .transition(DrawableTransitionOptions.withCrossFade(CROSS_FADE_DURATION))
            .apply(options)
            .into(binding.ivImg)
    }

    private fun initToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    // 更新歌曲信息
    private fun updateMediaMetadataUI() {
        val controller = this.controller ?: return
        if (controller == null || controller.mediaItemCount == 0) {
            binding.tvSongName.text = "加载中..."
            binding.tvArtName.text = "未加载"
            return
        }
        val mediaMetadata = controller.mediaMetadata
        binding.tvSongName.text = mediaMetadata.title ?: ""
        binding.tvArtName.text = mediaMetadata.artist ?: ""
        binding.tvDuration.text = controller.duration.toPlayerTime()
        binding.tvPosition.text = controller.currentPosition.toPlayerTime()
        updateTrackCover(mediaMetadata.artworkUri)
    }

    override fun onStop() {
        super.onStop()
        MediaController.releaseFuture(controllerFuture)
    }

    private fun initializeController() {
        controllerFuture =
            MediaController.Builder(
                this,
                SessionToken(this, ComponentName(this, PlaybackService::class.java)),
            )
                .buildAsync()
        // 等待连接
        controllerFuture.addListener({ setController() }, MoreExecutors.directExecutor())
    }

    private fun setController() {
        updateMediaMetadataUI()
        val controller = this.controller ?: return
        startTimer()
        controller.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                }
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {

                }
                if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                    updateMediaMetadataUI()
                }
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {

                }
            }
        })
        binding.sbSeek.setMediaController(controller)

        binding.btnPlay.setOnClickListener {
            if (!controller.isPlaying) {
                controller.play()
                binding.btnPlay.setIconResource(com.dafay.demo.lib.material.R.drawable.ic_stop_circle_24dp)
            } else {
                controller.pause()
                binding.btnPlay.setIconResource(com.dafay.demo.lib.material.R.drawable.ic_play_circle_24dp)
            }
        }

        binding.btnSkipNext.setOnClickListener {
            controller.seekToNext()
        }

        binding.btnSkipPrevious.setOnClickListener {
            controller.seekToPrevious()
        }

        controller.addListener(object : Player.Listener {
            @SuppressLint("UnsafeOptInUsageError")
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                debug("onAudioSessionIdChanged audioSessionId=$audioSessionId")
                initializeVisualizer(audioSessionId)
            }
        })
    }

    private fun initializeVisualizer(audioSessionId: Int) {
        visualizer = Visualizer(audioSessionId).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1]
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int
                ) {
                    // 更新 UI
//                    updateVisualizerUI(waveform)
                }

                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int
                ) {
                    // 更新 UI
//                    updateVisualizerUI(fft)
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)
            enabled = true
        }
    }

    private fun releaseVisualizer() {
        visualizer?.release()
        visualizer = null
    }


    private var disposable: Disposable? = null
    private fun startTimer(){
        disposable?.dispose()
        disposable=Observable.interval(1000, TimeUnit.MILLISECONDS)
            .subscribe {
                runOnUiThread {
                    val controller = this.controller ?: return@runOnUiThread
                    binding.tvPosition.text = controller.currentPosition.toPlayerTime()
                }
            }
    }
}


