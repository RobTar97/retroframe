package com.rober.photoframe

import android.app.Application
import com.rober.photoframe.data.AlarmSettings
import com.rober.photoframe.data.FavoritesManager
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.ui.ImageLoader

class PhotoframeApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Deliberately cheap: these are three SharedPreferences opens and one display
        // metrics read. Nothing here touches the network, and nothing scans storage —
        // cold start on a slow eMMC device is the first thing a user judges the app on.
        PhotoframePreferences.init(this)
        FavoritesManager.init(this)
        AlarmSettings.init(this)
        ImageLoader.init(this)
    }
}
