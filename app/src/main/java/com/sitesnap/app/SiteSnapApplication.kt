package com.sitesnap.app

import android.app.Application
import com.sitesnap.app.data.AppStore
import com.sitesnap.app.data.PhotoRepository
import com.sitesnap.app.drive.DriveSync
import com.sitesnap.app.update.AppUpdater
import java.io.File

class SiteSnapApplication : Application() {
    lateinit var store: AppStore
        private set
    lateinit var photos: PhotoRepository
        private set
    lateinit var driveSync: DriveSync
        private set
    lateinit var updater: AppUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        store = AppStore(File(filesDir, "sitesnap.json"))
        photos = PhotoRepository(this)
        driveSync = DriveSync(this, store, photos)
        updater = AppUpdater(this)
    }
}
