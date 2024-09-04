package com.dafay.demo.exoplayer.adapter

import androidx.media3.common.MediaItem
import com.dafay.demo.lib.base.ui.adapter.BaseAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dafay.demo.exoplayer.databinding.ItemPlayableBinding

class PlayableMediaItemArrayAdapter : BaseAdapter<MediaItem>() {

    var onItemClickListener: PlayableMediaItemViewHolder.OnItemClickListener? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return PlayableMediaItemViewHolder(ItemPlayableBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when {
            holder is PlayableMediaItemViewHolder -> {
                val item = datas[position]
                holder.binding.mediaItem.text=item.mediaMetadata.title
                holder.binding.addButton.setOnClickListener {
                    onItemClickListener?.onClickAdd(it,position,item)
                }
                holder.binding.llContainer.setOnClickListener {
                    onItemClickListener?.onItemClick(it,position,item)
                }
            }
        }
    }

    class PlayableMediaItemViewHolder : RecyclerView.ViewHolder {
        val binding: ItemPlayableBinding

        constructor(itemView: ItemPlayableBinding) : super(itemView.root) {
            binding = itemView
        }

        interface OnItemClickListener {
            fun onClickAdd(view: View, position: Int, mediaItem: MediaItem)
            fun onItemClick(view: View, position: Int, mediaItem: MediaItem)
        }
    }
}