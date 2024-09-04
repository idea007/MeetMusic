package com.dafay.demo.exoplayer.adapter

import androidx.media3.common.MediaItem
import com.dafay.demo.lib.base.ui.adapter.BaseAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.RecyclerView
import com.dafay.demo.exoplayer.R
import com.dafay.demo.exoplayer.databinding.ItemPlaylistBinding

class MediaItemListAdapter : BaseAdapter<MediaItem>() {

    var onItemClickListener: PlaylistViewHolder.OnItemClickListener? = null
     var controller: MediaController?=null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return PlaylistViewHolder(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when {
            holder is PlaylistViewHolder -> {
                val item = datas[position]
                holder.binding.mediaItem.text=item.mediaMetadata.title

                if (position == (controller?.currentMediaItemIndex?:-1)) {
                    holder.binding.root.setBackgroundColor(ContextCompat.getColor(holder.binding.root.context, R.color.playlist_item_background))
                    holder.binding.mediaItem.setTextColor(ContextCompat.getColor(holder.binding.root.context, R.color.white))
                    holder.binding.deleteButton.visibility = View.GONE
                }else{
                    holder.binding.deleteButton.visibility = View.VISIBLE
                    holder.binding.root.setBackgroundColor(ContextCompat.getColor(holder.binding.root.context, R.color.player_background))
                    holder.binding.mediaItem.setTextColor(ContextCompat.getColor(holder.binding.root.context, R.color.white))

                    holder.binding.deleteButton.setOnClickListener {
                       onItemClickListener?.onClickDelete(it,position,item)
                    }
                }
                holder.binding.root.setOnClickListener{
                    onItemClickListener?.onItemClick(it,position,item)
                }
            }
        }
    }

    class PlaylistViewHolder : RecyclerView.ViewHolder {
        val binding: ItemPlaylistBinding

        constructor(itemView: ItemPlaylistBinding) : super(itemView.root) {
            binding = itemView
        }

        interface OnItemClickListener {
            fun onClickDelete(view: View, position: Int, mediaItem: MediaItem)
            fun onItemClick(view: View, position: Int, mediaItem: MediaItem)
        }
    }
}