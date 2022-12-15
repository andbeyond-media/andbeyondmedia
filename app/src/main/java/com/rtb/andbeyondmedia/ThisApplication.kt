package com.rtb.andbeyondmedia

import android.app.Application
import com.rtb.andbeyondmedia.sdk.AndBeyondMedia

class ThisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AndBeyondMedia.initialize(this)
    }
}