package com.example.demo.meetsplash.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * @Des
 * @Author lipengfei
 * @Date 2023/12/29
 */

@Parcelize
data class Headers(
    val status: String,
    val code: Int,
    val error_message: String?,
    val warnings: String?,
    val results_count: Int,
    val next: String?
) : Parcelable


@Parcelize
data class Albums(val headers: Headers, val results: List<Album>) : Parcelable

@Parcelize
data class Album(
    val id: String,
    val name: String,
    val releasedate: String,
    val artist_id: String,
    val artist_name: String,
    val image: String,
    val zip: String,
    val shorturl: String,
    val shareurl: String,
    val zip_allowed: Boolean
) : Parcelable


@Parcelize
data class Artists(val headers: Headers, val results: List<Artist>) : Parcelable

@Parcelize
data class Artist(
    val id: String,
    val name: String,
    val website: String,
    val joindate: String,
    val image: String,
    val shorturl: String,
    val shareurl: String,
) : Parcelable


@Parcelize
data class Sheets(val headers: Headers, val results: List<Sheet>) : Parcelable

/**
 * 歌单 又名 playlist
 */
@Parcelize
data class Sheet(
    val id: String,
    val name: String,
    val creationdate: String,
    val user_id: String,
    val user_name: String,
    val zip: String,
    val shorturl: String,
    val shareurl: String,
) : Parcelable


@Parcelize
data class Tracks(val headers: Headers, val results: List<Track>) : Parcelable


data class AlbumsTracksResponse(val headers: Headers, val results: List<AlbumsTracks>)

data class AlbumsTracks(
    val id: String,
    val name: String,
    val releasedate: String,
    val artist_id: String,
    val artist_name: String,
    val image: String,
    val zip: String,
    val zip_allowed: Boolean,
    val tracks: List<Track>
)

/**
 * 歌单 又名 playlist
 */
@Parcelize
data class Track(
    val id: String,
    val name: String,
    val artist_id: String,
    val artist_name: String,
    val artist_idstr: String,
    val album_name: String,
    val album_id: String,
    val license_ccurl: String,
    val position: Int,
    val releasedate: String,
    val album_image: String,
    val audio: String,
    val audiodownload: String,
    val prourl: String,
    val shorturl: String,
    val shareurl: String,
    val waveform: String,
    val image: String,
    val audiodownload_allowed: Boolean,
    val duration: Int,
) : Parcelable

@Parcelize
data class Feeds(val headers: Headers, val results: List<Feed>) : Parcelable

@Parcelize
data class Feed(
    val id: String,
    val title: Title,
    val link: String,
    val position: String,
    val lang: List<String>,
    val date_start: String,
    val date_end: String,
    val text: Text,
    val type: String,
    val joinid: String,
//    val subtitle: List<String>?,
    val target: String,
    val images: Images,
) : Parcelable

@Parcelize
data class Title(val en: String) : Parcelable

@Parcelize
data class Lang(val en: String) : Parcelable

@Parcelize
data class Text(val en: String) : Parcelable

@Parcelize
data class Images(
    var size996_350: String?,
    var size315_111: String?,
    var size600_211: String?,
    var size470_165: String?,
) : Parcelable


