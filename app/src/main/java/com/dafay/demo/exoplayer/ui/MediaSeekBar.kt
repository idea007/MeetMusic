/*
 * Copyright (c) 2019 Naman Dwivedi.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.dafay.demo.exoplayer.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController

/**
 * SeekBar that can be used with a [MediaSessionCompat] to track and seek in playing
 * media.
 */

class MediaSeekBar : AppCompatSeekBar {

    private var mMediaController: MediaController? = null
    private var playerListener: MyPlayerListener? = null

    private var mIsTracking = false

    //get the global duration scale for animators, user may chane the duration scale from developer options
    //need to make sure our value animator doesn't change the duration scale
    private val mDurationScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    )

    private val mOnSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            mIsTracking = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            mMediaController?.seekTo(progress.toLong())
            mIsTracking = false
        }
    }
    private var mProgressAnimator: ValueAnimator? = null

    constructor(context: Context) : super(context) {
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener)
    }

    override fun setOnSeekBarChangeListener(l: SeekBar.OnSeekBarChangeListener) {
        // Prohibit adding seek listeners to this subclass.
        throw UnsupportedOperationException("Cannot add listeners to a MediaSeekBar")
    }

    fun setMediaController(mediaController: MediaController?) {
        if (mediaController != null) {
            playerListener = MyPlayerListener()
            mediaController.addListener(playerListener!!)
            playerListener!!.onMediaMetadataChanged(mediaController.mediaMetadata)
            playerListener!!.onPlaybackStateChanged(mediaController.playbackState)
        } else if (mMediaController != null) {
            mMediaController!!.addListener(playerListener!!)
            playerListener = null
        }
        mMediaController = mediaController
        playerListener!!.dealStateChange(mediaController!!.isPlaying)
    }

    fun disconnectController() {
        if (mMediaController != null) {
            mMediaController!!.removeListener(playerListener!!)
            playerListener = null
            mMediaController = null
        }
    }

    private inner class MyPlayerListener : Player.Listener, ValueAnimator.AnimatorUpdateListener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            dealStateChange(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            dealStateChange(playbackState== PlaybackState.STATE_PLAYING)
        }

         fun dealStateChange(isPlaying: Boolean) {
            mMediaController ?: return
            mProgressAnimator?.cancel()
            mProgressAnimator=null
            val progress = mMediaController!!.currentPosition.toInt()
            setProgress(progress)
            if (isPlaying) {
                val timeToEnd = (max - progress)
                if (timeToEnd > 0) {
                    mProgressAnimator?.cancel()
                    mProgressAnimator = ValueAnimator.ofInt(progress, max)
                        .setDuration((timeToEnd / mDurationScale).toLong())

                    mProgressAnimator!!.interpolator = LinearInterpolator()
                    mProgressAnimator!!.addUpdateListener(this)
                    mProgressAnimator!!.start()
                }
            } else {
                setProgress(mMediaController!!.currentPosition.toInt())
            }
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            super.onMediaMetadataChanged(mediaMetadata)
            mMediaController?.let {
                val max = it.duration.toInt()
                setMax(max)
                onPlaybackStateChanged(it.playbackState)
            }
        }

        override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
            // If the user is changing the slider, cancel the animation.
            if (mIsTracking) {
                valueAnimator.cancel()
                return
            }

            val animatedIntValue = valueAnimator.animatedValue as Int
            progress = animatedIntValue
        }

    }

}
