package com.bing.mmkvlib

import android.app.Application
import android.util.Log
import com.tencent.mmkv.MMKV



class MmkvApplication : Application() {
    companion object {
        const val TAG = "MmkvApplication"
    }

    override fun onCreate() {
        super.onCreate()
        val mmkvRootDir = MMKV.initialize(this)
        Log.i(TAG, "mmkv root: $mmkvRootDir")
    }
}