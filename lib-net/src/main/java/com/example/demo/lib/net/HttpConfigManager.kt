package com.example.demo.lib.net

import okhttp3.Interceptor

/**
 * @Des 网络请求配置
 * @Author dafay
 * @Date 2023/11/27 18:06
 */
object HttpConfigManager {


    var config: Config? = null


    fun initConfig(config: Config) {
        this.config = config
    }

    class Config private constructor(val baseUrl: String, val interceptors: ArrayList<Interceptor>) {

        companion object {
            inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
        }

        class Builder {
            var baseUrl: String = ""

            private val interceptors: ArrayList<Interceptor> = ArrayList()

            fun addInterceptor(interceptor: Interceptor) {
                interceptors.add(interceptor)
            }

            fun build() = Config(baseUrl, interceptors)
        }
    }

}