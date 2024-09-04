package com.dafay.demo.exoplayer.page.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter


class HomePageAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val datas = ArrayList<Fragment>()


    open fun setDatas(newDatas: List<Fragment>?) {
        datas.clear()
        if (!newDatas.isNullOrEmpty()) {
            datas.addAll(newDatas)
        }
        notifyDataSetChanged()
    }

    open fun addData(newData: Fragment) {
        val insertRangeStart = itemCount
        datas.add(newData)
        notifyItemRangeInserted(insertRangeStart, 1)
    }

    open fun addDatas(newDatas: List<Fragment>) {
        val insertRangeStart = itemCount
        datas.addAll(newDatas)
        notifyItemRangeInserted(insertRangeStart, newDatas.size)
    }

    override fun getItemCount(): Int {
        return datas.size
    }

    override fun createFragment(position: Int): Fragment {
        return datas.get(position)
    }
}
