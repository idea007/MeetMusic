package com.dafay.demo.exoplayer.page.main

import androidx.activity.OnBackPressedCallback
import com.dafay.demo.biz.settings.base.BaseThemeActivity
import com.dafay.demo.exoplayer.R
import com.dafay.demo.exoplayer.databinding.ActivityNewHomeBinding
import com.dafay.demo.exoplayer.page.main.feeds.FeedsFragment

class NewHomeActivity : BaseThemeActivity<ActivityNewHomeBinding>(ActivityNewHomeBinding::inflate) {

    override fun initViews() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        supportFragmentManager.beginTransaction()
            .apply {
                replace(R.id.fl_container, FeedsFragment())
                commit()
            }

//        supportFragmentManager.beginTransaction().apply {
//            replace(R.id.fl_bottom_controls, BottomControlsFragment())
//            addToBackStack(null)
//            commit()
//        }
    }


}
