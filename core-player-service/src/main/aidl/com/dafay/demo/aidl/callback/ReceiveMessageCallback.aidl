package com.dafay.demo.aidl.callback;

interface ReceiveMessageCallback {
    void onFFTReady(int sampleRateHz, int channelCount, in float[] fft);
}