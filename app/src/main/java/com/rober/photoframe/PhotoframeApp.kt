package com.rober.photoframe

import android.app.Application
import com.rober.photoframe.settings.PhotoframePreferences

class PhotoframeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PhotoframePreferences.init(this)
        com.rober.photoframe.data.FavoritesManager.init(this)
        com.rober.photoframe.data.AlarmManager.init(this)
    }
}
