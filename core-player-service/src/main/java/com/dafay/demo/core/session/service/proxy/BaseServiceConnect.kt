package com.dafay.demo.aidl.proxy

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.dafay.demo.aidl.ExtraSessionInterface
import com.dafay.demo.aidl.callback.ReceiveMessageCallback
import com.dafay.demo.aidl.callback.ServiceConnectCallback
import com.dafay.demo.lib.base.utils.ApplicationUtils
import com.dafay.demo.lib.base.utils.HandlerUtils
import com.dafay.demo.lib.base.utils.debug
import java.util.concurrent.CopyOnWriteArrayList


// 服务端的 applicationId
private const val SERVICE_PKG_NAME = "com.dafay.demo.exoplayer"

// 服务端的 Service
private const val SERVICE_CLASS_NAME = "com.dafay.demo.core.session.service.ExtraService"

/**
 * 常规方式
 * 调用 aidl 接口，如果服务未连接便抛出异常
 */
abstract class BaseServiceConnect {
    private var application: Application = ApplicationUtils.getApp()
    private var intent: Intent = Intent().apply { component = ComponentName(SERVICE_PKG_NAME, SERVICE_CLASS_NAME) }
    @Volatile
    protected var remoteServiceProxy: ExtraSessionInterface? = null
    protected var receiveListeners = CopyOnWriteArrayList<ReceiveMessageCallback.Stub>()

    private val receiveMessageCallback: ReceiveMessageCallback = object : ReceiveMessageCallback.Stub() {
        override fun onFFTReady(sampleRateHz: Int, channelCount: Int, fft: FloatArray?) {
            HandlerUtils.mainHandler.post {
                receiveListeners.forEach { it.onFFTReady(sampleRateHz,channelCount,fft) }
            }
        }
    }
    private val serviceConnectCallback: ServiceConnectCallback = object : ServiceConnectCallback.Stub() {
        @Throws(RemoteException::class)
        override fun onConnectReply(message: String) {
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            debug("onServiceConnected")
            remoteServiceProxy = ExtraSessionInterface.Stub.asInterface(service)
            service.linkToDeath(object : IBinder.DeathRecipient {
                override fun binderDied() {
                    debug("binderDied")
                    release()
                }
            }, 0)
            remoteServiceProxy!!.registerServiceCallback(serviceConnectCallback)
            remoteServiceProxy!!.registerReceiveMessageCallback(receiveMessageCallback)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            debug("onServiceDisconnected")
            release()
            autoReconnect()
        }
    }

    fun bindToService(): Boolean {
        if (remoteServiceProxy != null && remoteServiceProxy!!.asBinder().isBinderAlive) {
            return true
        }
        // bindService 的 ServiceConnection 回调是在主线程中
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        return remoteServiceProxy != null
    }

    /**
     * 自动重连机制
     */
    private fun autoReconnect() {
        HandlerUtils.mainHandler.postDelayed({
            bindToService()
        }, 5 * 1000)
    }

    protected fun isServiceConnected(): Boolean {
        return bindToService()
    }

    private fun release() {
        remoteServiceProxy = null
    }
}

