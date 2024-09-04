package com.dafay.demo.biz.settings.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.dafay.demo.biz.settings.COLOR_THEME
import com.dafay.demo.biz.settings.PrefC
import com.dafay.demo.biz.settings.helper.ThemeHelper
import com.dafay.demo.lab.base.base.BaseActivity
import com.example.demo.biz.base.storage.sp.SPUtils

/**
 * @Des
 * @Author lipengfei
 * @Date 2024/1/23
 */

abstract class BaseThemeActivity<VB : ViewBinding>(private val inflate: (LayoutInflater) -> VB) : BaseActivity<VB>(inflate) {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    private fun applyTheme() {
        val colorTheme = COLOR_THEME.from(SPUtils.findPreference(PrefC.COLOR_THEME, COLOR_THEME.DYNAMIC.key))
        ThemeHelper.applyTheme(colorTheme, this)
    }
}