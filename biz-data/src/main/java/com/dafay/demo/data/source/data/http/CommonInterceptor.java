package com.dafay.demo.data.source.data.http;

import com.example.demo.meetsplash.data.model.ConfigC;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @Des
 * @Author lipengfei
 * @Date 2023/12/29
 */
public class CommonInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request oldRequest = chain.request();
        // 添加新的参数
        HttpUrl.Builder authorizedUrlBuilder = oldRequest.url()
                .newBuilder()
                .scheme(oldRequest.url().scheme())
                .host(oldRequest.url().host())
                .addQueryParameter("format","json")
                .addQueryParameter("client_id", ConfigC.INSTANCE.getJAMENDO_CLIENT_ID());
        // 新的请求
        Request newRequest = oldRequest.newBuilder()
                .method(oldRequest.method(), oldRequest.body())
                .url(authorizedUrlBuilder.build())
                .build();
        return chain.proceed(newRequest);
    }
}
