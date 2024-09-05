package com.dafay.demo.aidl.proxy

import com.dafay.demo.aidl.callback.ReceiveMessageCallback


object ExtraSessionServiceProxy : BaseServiceConnect() {

    @Throws(RuntimeException::class)
    private fun checkServiceConnected() {
        if (!isServiceConnected()) {
            throw RuntimeException("service not connected")
        }
    }

    fun bindService() {
        bindToService()
    }

    fun registerReceiverListener(listener: ReceiveMessageCallback.Stub?) {
        if (!receiveListeners.contains(listener)) {
            receiveListeners.add(listener)
        }
    }

    fun unregisterReceiverListener(listener: ReceiveMessageCallback.Stub?) {
        if (receiveListeners.contains(listener)) {
            receiveListeners.remove(listener)
        }
    }
}
