package com.example.photocleanup

import android.app.Application
import com.example.photocleanup.data.PhotoDatabase

class PhotoCleanupApp : Application() {
    val database: PhotoDatabase by lazy { PhotoDatabase.getDatabase(this) }
}
