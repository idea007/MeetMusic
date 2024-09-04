package com.dafay.demo.exoplayer.adapter

import androidx.media3.common.MediaItem
import com.dafay.demo.lib.base.ui.adapter.BaseAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dafay.demo.exoplayer.databinding.ItemFolderBinding

class FolderAdapter : BaseAdapter<MediaItem>() {

    var onItemClickListener: FolderViewHolder.OnItemClickListener? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return FolderViewHolder(ItemFolderBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when {
            holder is FolderViewHolder -> {
                val item = datas[position]
                holder.binding.mediaItem.text = item.mediaMetadata.title
                holder.binding.mediaItem.setOnClickListener {
                    onItemClickListener?.onClickItem(it, position, item)
                }
            }
        }
    }

    class FolderViewHolder : RecyclerView.ViewHolder {
        val binding: ItemFolderBinding

        constructor(itemView: ItemFolderBinding) : super(itemView.root) {
            binding = itemView
        }

        interface OnItemClickListener {
            fun onClickItem(view: View, position: Int, mediaItem: MediaItem)
        }
    }
}