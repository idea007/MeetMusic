package com.example.demo.meetsplash.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
data class Tracks(val headers: Headers, val results: List<Track>) : Parcelable

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


