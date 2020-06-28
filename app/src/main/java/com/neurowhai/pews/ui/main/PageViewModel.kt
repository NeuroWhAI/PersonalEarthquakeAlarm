package com.neurowhai.pews.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    val url: LiveData<String> = Transformations.map(_index) {
        when (it) {
            1 -> "https://www.weather.go.kr/pews/"
            else -> "https://www.weather.go.kr/pews/man/m.html"
        }
    }

    fun setIndex(index: Int) {
        _index.value = index
    }

    fun getIndex(): Int = _index.value ?: 1
}