package com.example.demo.meetsplash.data.http

import com.example.demo.meetsplash.data.model.Albums
import com.example.demo.meetsplash.data.model.AlbumsTracksResponse
import com.example.demo.meetsplash.data.model.Artists
import com.example.demo.meetsplash.data.model.Feeds
import com.example.demo.meetsplash.data.model.Sheets
import com.example.demo.meetsplash.data.model.Tracks
import io.reactivex.Observable
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * @ClassName JamendoService
 * @Des
 * @Author lipengfei
 * @Date 2023/11/27 18:59
 */
interface JamendoService {

    @GET("/v3.0/feeds")
     fun getFeeds(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("type") type: String,
        @Query("lang") lang: String="en",
    ): Observable<Feeds>


    @GET("/v3.0/albums")
    fun getAlbums(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("order") order: String,
        @Query("imagesize") imagesize: Int = 200,
        @Query("audioformat") audioformat: String = "mp32",
    ): Observable<Albums>

    @GET("/v3.0/albums/tracks")
    fun getAlbumsTracks(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("id") id: Int,
        @Query("order") order: String="track_position",
        @Query("imagesize") imagesize: Int = 200,
        @Query("audioformat") audioformat: String = "mp32",
    ): Observable<AlbumsTracksResponse>

    @GET("/v3.0/albums/tracks")
    fun getAlbumsTracksObservable(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("id") id: Int,
        @Query("order") order: String="track_position",
        @Query("imagesize") imagesize: Int = 200,
        @Query("audioformat") audioformat: String = "mp32",
    ): Observable<AlbumsTracksResponse>



    @GET("/v3.0/artists")
    fun getArtists(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("order") order: String,
        @Query("imagesize") imagesize: Int = 200,
        @Query("audioformat") audioformat: String = "mp32",
    ): Observable<Artists>


    @GET("/v3.0/playlists")
    fun getPlaylists(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("order") order: String,
        @Query("audioformat") audioformat: String = "mp32",
    ): Observable<Sheets>


    @GET("/v3.0/tracks")
    fun getTracks(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("order") order: String="listens_week",
        @Query("audioformat") audioformat: String = "mp32",
    ): Observable<Tracks>
}