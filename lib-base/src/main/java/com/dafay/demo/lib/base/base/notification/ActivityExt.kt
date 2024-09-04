package com.dafay.demo.lib.base.base.notification

import android.app.Activity
import android.app.NotificationManager
import android.app.Service
import android.os.Build
import android.view.View
import com.google.android.material.snackbar.Snackbar

fun Activity.getNotificationManager(): NotificationManager {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this.getSystemService(NotificationManager::class.java)
    } else {
        getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
    }
}


fun View.showSnackbar(
    msg: String,
    actionMessage: CharSequence?,
    action: (View) -> Unit
) {
    val snackbar = Snackbar.make(this, msg, Snackbar.LENGTH_INDEFINITE)
    if (actionMessage != null) {
        snackbar.setAction(actionMessage) {
            action(this)
        }
    }
    snackbar.show()
}