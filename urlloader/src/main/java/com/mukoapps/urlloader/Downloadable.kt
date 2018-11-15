package com.mukoapps.urlloader

import android.util.Log

abstract class Downloadable<T>(var url: String) {
    private var cancelled = false
    private var used = false
    private lateinit var onLoad: (T) -> Unit
    abstract fun transform(content: DownloadableContent): T
    fun load(onLoad: (T) -> Unit) {
        Log.e("downloadable", "load()")
        if (used)
            throw IllegalStateException("This can be called only once per downloadable")
        used = true
        this.onLoad = onLoad
        Loader.load(this)
    }

    fun callOnLoad(content: DownloadableContent) {
        Log.e("downloadable", "called on load")
        if (cancelled)
            return
        Log.e("downloadable", "calling on load with transform")
        onLoad(transform(content))
    }

    fun cancel() {
        cancelled = true
        Loader.cancel(this)
    }
}