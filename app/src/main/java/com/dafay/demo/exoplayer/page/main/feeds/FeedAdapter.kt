package com.dafay.demo.exoplayer.page.main.feeds

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.dafay.demo.exoplayer.databinding.ItemFeedBinding
import com.dafay.demo.exoplayer.glide.GlideApp
import com.dafay.demo.lib.base.ui.adapter.BaseAdapter

val colors = arrayOf(
    Color.parseColor("#EBD9CB"), Color.parseColor("#F9F6F1"), Color.parseColor("#DBE2EC"),
    Color.parseColor("#8D91AA"),
    Color.parseColor("#E2E2E2"),
    Color.parseColor("#DECECE"),
    Color.parseColor("#F7F0EA"),
    Color.parseColor("#E7ADAC"),
    Color.parseColor("#78677A"),
    Color.parseColor("#D8B0B0"),
    Color.parseColor("#F0F0F2"),
    Color.parseColor("#ACD4D6"),
    Color.parseColor("#797979"),
)

const val CROSS_FADE_DURATION = 350

class FeedAdapter : BaseAdapter<MediaItem>() {

    var onItemClickListener: AlbumViewHolder.OnItemClickListener? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AlbumViewHolder(ItemFeedBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AlbumViewHolder) {
            holder.onBindViewHolder(position, datas[position], onItemClickListener)
        }
    }


    class AlbumViewHolder : RecyclerView.ViewHolder {

        val binding: ItemFeedBinding

        constructor(itemView: ItemFeedBinding) : super(itemView.root) {
            binding = itemView
        }

        @SuppressLint("UnsafeOptInUsageError")
        fun onBindViewHolder(position: Int, mediaItem: MediaItem, onItemClickListener: OnItemClickListener? = null) {

            binding.mcvCard.setCardBackgroundColor(colors[position % colors.size])
            binding.tvTitle.text = mediaItem.mediaMetadata.title

            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(Target.SIZE_ORIGINAL)
                .dontTransform()

            GlideApp.with(binding.ivImg)
                .load(mediaItem.mediaMetadata.artworkUri)
                .transition(DrawableTransitionOptions.withCrossFade(CROSS_FADE_DURATION))
                .apply(options)
                .into(binding.ivImg)

            binding.mcvCard.setOnClickListener {
                onItemClickListener?.onClickItem(it, position, mediaItem)
            }
        }

        interface OnItemClickListener {
            fun onClickItem(view: View, position: Int, mediaItem: MediaItem)
        }
    }

}