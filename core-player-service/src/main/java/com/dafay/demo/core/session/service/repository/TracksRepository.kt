package com.dafay.demo.core.session.service.repository

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dafay.demo.core.session.service.MediaItemConvertUtils
import com.dafay.demo.lib.base.utils.err
import com.example.demo.lib.net.RetrofitManager
import com.example.demo.meetsplash.data.http.JamendoService
import com.example.demo.meetsplash.data.model.Track
import io.reactivex.schedulers.Schedulers

object TracksRepository {
    fun getTracks(limit: Int, offset: Int): List<MediaItem> {
        val tracks: List<Track>
        try {
            tracks = RetrofitManager.createService(JamendoService::class.java)
                .getTracks(offset, limit, order = "popularity_total")
                .subscribeOn(Schedulers.io())
                .blockingFirst().results
        } catch (e: Exception) {
            err(e.message)
            return emptyList()
        }
        if (tracks.isEmpty()) {
            return emptyList()
        }
        val children = tracks.map {
            MediaItemConvertUtils.buildMediaItem(
                it.name,
                it.id,
                true,
                false,
                MediaMetadata.MEDIA_TYPE_MUSIC,
                mutableListOf(),
                album = it.album_name,
                artist = it.artist_name,
                imageUri = it.image.toUri(),
                sourceUri = it.audio.toUri()
            )
        }

        return children
    }
}