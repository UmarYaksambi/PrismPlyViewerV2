package com.example.plyviewer360

import android.app.Application
import com.google.android.filament.utils.Utils

class PlyViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Utils.init()
    }
}