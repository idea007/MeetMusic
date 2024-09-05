package com.dafay.demo.aidl;

import com.dafay.demo.aidl.callback.ServiceConnectCallback;
import com.dafay.demo.aidl.callback.ReceiveMessageCallback;
interface ExtraSessionInterface {

      int registerReceiveMessageCallback(in ReceiveMessageCallback callback);

      int unregisterReceiveMessageCallback(in ReceiveMessageCallback callback);

      oneway void registerServiceCallback(in ServiceConnectCallback callback);

      oneway void unregisterServiceCallback(in ServiceConnectCallback callback);

}