package com.dafay.demo.aidl.callback;

interface ServiceConnectCallback {
    oneway void onConnectReply(String message);
}