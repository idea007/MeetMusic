package com.example.demo.meetsplash.data.http

import com.example.demo.meetsplash.data.model.Tracks
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * @ClassName JamendoService
 * @Des
 * @Author dafay
 * @Date 2023/11/27 18:59
 */
interface JamendoService {

    @GET("/v3.0/tracks")
    fun getTracks(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("order") order: String = "listens_week",
        @Query("audioformat") audioformat: String = "mp32",
    ): Observable<Tracks>

}