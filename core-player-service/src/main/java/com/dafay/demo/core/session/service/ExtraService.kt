package com.dafay.demo.core.session.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.dafay.demo.aidl.ExtraSessionInterface
import com.dafay.demo.aidl.callback.ReceiveMessageCallback
import com.dafay.demo.aidl.callback.ServiceConnectCallback
import com.dafay.demo.lib.base.utils.debug

class ExtraService : Service() {


    private val deathRecipientMap: HashMap<Int, ClientDeathRecipient> = HashMap()

    companion object {
        val receiveMessageCallbacks: HashMap<Int, ReceiveMessageCallback> = HashMap()
    }

    override fun onBind(intent: Intent): IBinder {
        return TestStub()
    }

    /**
     * 没有客户端与其绑定时，便会销毁
     */
    override fun onDestroy() {
        super.onDestroy()
        debug("onDestroy()")
    }

    inner class TestStub : ExtraSessionInterface.Stub() {

        override fun registerReceiveMessageCallback(callback: ReceiveMessageCallback?): Int {
            callback ?: return -1
            receiveMessageCallbacks[getCallingPid()] = callback
            return 0
        }

        override fun unregisterReceiveMessageCallback(callback: ReceiveMessageCallback?): Int {
            callback ?: return -1
            receiveMessageCallbacks.remove(getCallingPid())
            return 0
        }

        override fun registerServiceCallback(callback: ServiceConnectCallback?) {
            callback ?: return
            val deathRecipient = ClientDeathRecipient(getCallingPid(), callback)
            deathRecipientMap[getCallingPid()] = deathRecipient
            callback.asBinder().linkToDeath(deathRecipient, 0)
        }

        override fun unregisterServiceCallback(callback: ServiceConnectCallback?) {
            if (deathRecipientMap.containsKey(getCallingPid())) {
                val deathRecipient: ClientDeathRecipient? = deathRecipientMap.remove(getCallingPid())
                deathRecipient?.callBack?.asBinder()?.unlinkToDeath(deathRecipient, 0)
            }
        }
    }

    inner class ClientDeathRecipient(private val pid: Int, val callBack: ServiceConnectCallback) :
        IBinder.DeathRecipient {
        override fun binderDied() {
            debug("receive client death recipient")
            deathRecipientMap.remove(pid)
            // 移除对应死亡客户端的回调，否则会触发 DeadObjectException
            receiveMessageCallbacks.remove(pid)
        }
    }
}