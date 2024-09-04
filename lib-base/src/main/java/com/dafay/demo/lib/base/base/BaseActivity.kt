package com.dafay.demo.lab.base.base

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding>(private val inflate: (LayoutInflater) -> VB) : AppCompatActivity() {
    lateinit var binding: VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = inflate(layoutInflater)
        setContentView(binding.root)
        resolveIntent(intent)
        initViews()
        initObserver()
        bindListener()
        initializeData()
    }

    protected open fun resolveIntent(intent: Intent?) {}

    protected open fun initViews() {}

    protected open fun initObserver() {}

    protected open fun bindListener() {}

    protected open fun initializeData() {}
}