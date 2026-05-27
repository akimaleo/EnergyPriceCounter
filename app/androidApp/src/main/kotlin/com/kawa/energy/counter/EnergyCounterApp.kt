package com.kawa.energy.counter

import android.app.Application
import com.kawa.energy.counter.data.monitor.AndroidContextHolder

class EnergyCounterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.init(this)
    }
}
