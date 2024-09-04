package com.example.demo.wander.music.home.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dafay.demo.exoplayer.databinding.ItemAlbumBinding
import com.dafay.demo.exoplayer.glide.GlideApp
import com.dafay.demo.lib.base.ui.adapter.BaseAdapter
import com.dafay.demo.lib.base.utils.debug


class AlbumAdapter : BaseAdapter<MediaItem>() {
    var onItemClickListener: AlbumViewHolder.OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AlbumViewHolder(ItemAlbumBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AlbumViewHolder) {
            holder.onBindViewHolder(position, datas[position], onItemClickListener)
        }
    }


    class AlbumViewHolder : RecyclerView.ViewHolder {

        val binding: ItemAlbumBinding

        constructor(itemView: ItemAlbumBinding) : super(itemView.root) {
            binding = itemView
        }

        fun onBindViewHolder(position: Int, mediaItem: MediaItem, onItemClickListener: OnItemClickListener? = null) {

            binding.tvTitle.text = mediaItem.mediaMetadata.title
            binding.tvDes.text = mediaItem.mediaMetadata.artist
            debug("mediaItem: ${mediaItem.mediaMetadata.title} ")
            debug("mediaItem: ${mediaItem.mediaMetadata.artist} ")
            debug("mediaItem: ${mediaItem.mediaMetadata.artworkUri} ")
            GlideApp.with(binding.ivImg)
                .load(mediaItem.mediaMetadata.artworkUri)
                .into(binding.ivImg)

//            val options = RequestOptions()
//                .diskCacheStrategy(DiskCacheStrategy.ALL)
//                .override(Target.SIZE_ORIGINAL)
//                .dontTransform()
//            GlideApp.with(binding.root.context)
//                .load(album.image)
//                .apply(options)
//                .into(binding.ivImg)

            binding.mcvCard.setOnClickListener {
                onItemClickListener?.onClickItem(it, position, mediaItem)
            }
        }

        interface OnItemClickListener {
            fun onClickItem(view: View, position: Int, mediaItem: MediaItem)
        }
    }

}