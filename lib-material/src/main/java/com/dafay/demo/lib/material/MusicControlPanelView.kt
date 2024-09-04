package com.dafay.demo.lib.material

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.dafay.demo.lib.material.databinding.LayoutMusicControlPanelBinding


/**
 * 一个测试按钮的容器视图，方便快速添加一些列按钮
 */
class MusicControlPanelView @kotlin.jvm.JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var _binding: LayoutMusicControlPanelBinding? = null
    val binding get() = _binding!!

    init {
        _binding = LayoutMusicControlPanelBinding.inflate(LayoutInflater.from(context), this, true)
        initViews()
    }

    private fun initViews() {
        binding.btnPlay.setOnClickListener {

        }
    }

    fun updatePlayBtnIcon(isPlaying: Boolean) {
        if (isPlaying) {
            binding.btnPlay.setIconResource(R.drawable.ic_stop_circle_24dp)
        } else {
            binding.btnPlay.setIconResource(R.drawable.ic_play_circle_24dp)
        }
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        _binding = null
    }

}