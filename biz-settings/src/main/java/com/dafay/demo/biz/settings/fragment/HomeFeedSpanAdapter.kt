package com.dafay.demo.biz.settings.fragment

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dafay.demo.biz.settings.R
import com.dafay.demo.biz.settings.databinding.ItemLanguageBinding
import com.dafay.demo.lib.base.ui.adapter.BaseAdapter
import com.google.android.material.color.MaterialColors

class HomeFeedSpanAdapter(currentSpanCount: Int) : BaseAdapter<Int>() {
    var onItemClickListener: HomeFeedSpanViewHolder.OnItemClickListener? = null
    var selectedSpanCount: Int = currentSpanCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return HomeFeedSpanViewHolder(ItemLanguageBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as HomeFeedSpanViewHolder).onBindViewHolder(position, datas[position], onItemClickListener, selectedSpanCount)
    }

    class HomeFeedSpanViewHolder(private val binding: ItemLanguageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun onBindViewHolder(
            position: Int,
            spanCount: Int,
            onItemClickListener: OnItemClickListener?,
            selectedSpanCount: Int
        ) {
            binding.uvItem.binding.apply {
                tvTitle.text = binding.root.context.getString(R.string.home_feed_span_count_value, spanCount)
                tvDes.visibility = View.GONE
                mcvCard.setCardBackgroundColor(Color.TRANSPARENT)
                mcvCard.setOnClickListener {
                    onItemClickListener?.onClickItem(it, position, spanCount)
                }
                if (selectedSpanCount == spanCount) {
                    ivIcon.visibility = View.VISIBLE
                    mcvCard.setCardBackgroundColor(
                        MaterialColors.getColor(
                            mcvCard.context,
                            com.google.android.material.R.attr.colorSurfaceContainerHigh,
                            HomeFeedSpanViewHolder::class.java.canonicalName
                        )
                    )
                } else {
                    ivIcon.visibility = View.INVISIBLE
                    mcvCard.setCardBackgroundColor(Color.TRANSPARENT)
                }
            }
        }

        interface OnItemClickListener {
            fun onClickItem(view: View, position: Int, spanCount: Int)
        }
    }
}
