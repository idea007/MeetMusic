package com.dafay.demo.lib.base.base.notification

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.dafay.demo.lab.base.base.BaseActivity
import com.dafay.demo.lib.base.utils.info
import com.google.android.material.snackbar.Snackbar

abstract class BaseNotificationPermissionActivity<VB : ViewBinding>(private val inflate: (LayoutInflater) -> VB) : BaseActivity<VB>(inflate) {


    protected val MY_CHANNEL_ID = "MY_CHANNEL_ID"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initNotificationChannel()
    }

    private fun initNotificationChannel() {
        // 您必须先创建通知渠道，然后才能在 Android 8.0 及更高版本上发布任何通知，因此请在应用启动时立即执行此代码。您可以放心地重复调用此方法，因为创建现有通知渠道不会执行任何操作。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(MY_CHANNEL_ID, "My Channel Name", NotificationManager.IMPORTANCE_DEFAULT)
            getNotificationManager().createNotificationChannel(channel)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                info("Permission: Granted")
            } else {
                info("Permission: Denied")
                openNotificationSettings(this)
            }
        }


    fun onClickRequestPermission(view: View) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                binding.root.showSnackbar(

                    "permission_granted",
                    null
                ) {}
            }

            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS) -> {
                binding.root.showSnackbar(

                    "permission_required",
                    "ok"
                ) {
                    requestPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }

            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    // 打开通知设置界面
    fun openNotificationSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, activity.applicationInfo.uid)
            activity.startActivity(intent)
        } else {
            // 在部分手机上，跳转到应用信息界面
            openAppDetailsSettings(activity)
        }
    }

    // 打开应用信息界面
    private fun openAppDetailsSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.setData(Uri.fromParts("package", activity.packageName, null))
        activity.startActivity(intent)
    }
}