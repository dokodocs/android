package com.bhrikuty.dokodocs

import android.app.Application
import com.bhrikuty.dokodocs.data.local.DokoDocsDatabase

class DokoDocsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-initialize Room Database
        DokoDocsDatabase.getInstance(this)
    }
}
