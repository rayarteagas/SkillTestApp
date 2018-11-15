package com.mukoapps.urlloader

import android.util.Log

class DownloadableString(url: String) : Downloadable<String>(url) {
    override fun transform(content: DownloadableContent): String {
        val str = content.toString(Charsets.UTF_8)
        Log.e("DownloadableString", str)
        return str
    }
}