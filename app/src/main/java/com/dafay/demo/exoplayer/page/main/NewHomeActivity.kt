package com.dafay.demo.exoplayer.page.main

import com.dafay.demo.exoplayer.R
import com.dafay.demo.exoplayer.databinding.ActivityNewHomeBinding
import com.dafay.demo.exoplayer.page.main.feeds.FeedsFragment
import com.dafay.demo.lab.base.base.BaseActivity

class NewHomeActivity : BaseActivity<ActivityNewHomeBinding>(ActivityNewHomeBinding::inflate) {

    override fun initViews() {
        supportFragmentManager.beginTransaction()
            .apply {
                replace(R.id.fl_container, FeedsFragment())
                addToBackStack(null)
                commit()
            }

//        supportFragmentManager.beginTransaction().apply {
//            replace(R.id.fl_bottom_controls, BottomControlsFragment())
//            addToBackStack(null)
//            commit()
//        }
    }


}