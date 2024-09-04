package com.dafay.demo.exoplayer.page.main.home

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.MarginPageTransformer
import com.dafay.demo.biz.settings.SettingsActivity
import com.dafay.demo.exoplayer.R
import com.dafay.demo.exoplayer.databinding.FragmentHomeBinding
import com.dafay.demo.exoplayer.page.main.HomePageAdapter
import com.dafay.demo.exoplayer.page.main.feeds.FeedsFragment
import com.dafay.demo.lab.base.base.BaseFragment


class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val fragments = ArrayList<BaseFragment<*>>()
    private lateinit var homePageAdapter: HomePageAdapter

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun initViews() {
        setHasOptionsMenu(true)
        // fragment 中设置 toolbar
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        initViewPager2()

    }

    private fun initViewPager2() {
        homePageAdapter = HomePageAdapter(requireActivity())
        binding.vpViewpager2.offscreenPageLimit = 1
        binding.vpViewpager2.setPageTransformer(MarginPageTransformer(5))
        binding.vpViewpager2.adapter = homePageAdapter
    }

    override fun initializeData() {
        super.initializeData()
        fragments.apply {
            add(FeedsFragment())
//            add(AlbumsFragment())
        }
        homePageAdapter.setDatas(fragments)
    }

}