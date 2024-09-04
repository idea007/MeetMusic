package com.dafay.demo.exoplayer

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getActivity
import android.content.Intent
import android.os.Build
import androidx.core.app.TaskStackBuilder
import com.dafay.demo.core.session.service.DemoPlaybackService
import com.dafay.demo.exoplayer.page.main.NewHomeActivity
import com.dafay.demo.exoplayer.page.player.NowPlayingActivity
import com.dafay.demo.lib.base.utils.debug

class PlaybackService : DemoPlaybackService() {

  companion object {
    private val immutableFlag = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
  }

  override fun getSingleTopActivity(): PendingIntent? {
    debug("getSingleTopActivity")
    return getActivity(
      this,
      0,
      Intent(this, NowPlayingActivity::class.java),
      immutableFlag or FLAG_UPDATE_CURRENT
    )
  }

  override fun getBackStackedActivity(): PendingIntent? {
    debug("getBackStackedActivity")
    return TaskStackBuilder.create(this).run {
      addNextIntent(Intent(this@PlaybackService, NewHomeActivity::class.java))
      addNextIntent(Intent(this@PlaybackService, NowPlayingActivity::class.java))
      getPendingIntent(0, immutableFlag or FLAG_UPDATE_CURRENT)
    }
  }
}
