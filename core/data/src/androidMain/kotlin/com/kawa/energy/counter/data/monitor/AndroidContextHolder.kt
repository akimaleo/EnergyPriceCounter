package com.kawa.energy.counter.data.monitor

import android.content.Context

object AndroidContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun require(): Context =
        appContext ?: error("AndroidContextHolder.init(context) must be called before using shared monitors")
}
