package com.dafay.demo.biz.settings.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.dafay.demo.biz.settings.DefC
import com.dafay.demo.biz.settings.PrefC
import com.dafay.demo.biz.settings.databinding.FragmentBottomSheetSelectPictureQualityBinding
import com.dafay.demo.biz.settings.helper.CommonMessage
import com.dafay.demo.lib.base.storage.sp.SPUtils
import com.dafay.demo.lib.base.utils.RxBus
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SelectHomeFeedSpanBottomSheetDialogFragment(
    private val callback: SpanCountChangeCallback?
) : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentBottomSheetSelectPictureQualityBinding
    private lateinit var homeFeedSpanAdapter: HomeFeedSpanAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentBottomSheetSelectPictureQualityBinding.inflate(LayoutInflater.from(requireContext()))
        val bottomSheetDialog = BottomSheetDialog(requireContext(), theme)
        bottomSheetDialog.setContentView(binding.root)
        initViews()
        return bottomSheetDialog
    }

    private fun initViews() {
        val currentSpanCount = SPUtils.findPreference(PrefC.HOME_FEED_SPAN_COUNT, DefC.HOME_FEED_SPAN_COUNT)
            .coerceIn(DefC.HOME_FEED_MIN_SPAN_COUNT, DefC.HOME_FEED_MAX_SPAN_COUNT)
        homeFeedSpanAdapter = HomeFeedSpanAdapter(currentSpanCount)
        binding.rvRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecyclerview.adapter = homeFeedSpanAdapter
        homeFeedSpanAdapter.onItemClickListener = object : HomeFeedSpanAdapter.HomeFeedSpanViewHolder.OnItemClickListener {
            override fun onClickItem(view: View, position: Int, spanCount: Int) {
                if (homeFeedSpanAdapter.selectedSpanCount != spanCount) {
                    val previousPosition = homeFeedSpanAdapter.datas.indexOf(homeFeedSpanAdapter.selectedSpanCount)
                    homeFeedSpanAdapter.selectedSpanCount = spanCount
                    if (previousPosition != -1) {
                        homeFeedSpanAdapter.notifyItemChanged(previousPosition)
                    }
                    homeFeedSpanAdapter.notifyItemChanged(position)
                    SPUtils.putPreference(PrefC.HOME_FEED_SPAN_COUNT, spanCount)
                    callback?.onSpanCountChange(spanCount)
                    RxBus.post(CommonMessage(CommonMessage.Type.CHANGE_HOME_FEED_SPAN_COUNT))
                }
                dismiss()
            }
        }
        homeFeedSpanAdapter.setDatas((DefC.HOME_FEED_MIN_SPAN_COUNT..DefC.HOME_FEED_MAX_SPAN_COUNT).toList())
    }

    interface SpanCountChangeCallback {
        fun onSpanCountChange(spanCount: Int)
    }
}
