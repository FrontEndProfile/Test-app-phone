package com.fazal.antisocial

import android.app.Application
import com.fazal.antisocial.data.Store

class AntiSocialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
