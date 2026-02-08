package com.example.photocleanup

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.example.photocleanup.data.AppPreferences
import com.example.photocleanup.data.PhotoDatabase

class PhotoCleanupApp : Application(), ImageLoaderFactory {
    val database: PhotoDatabase by lazy { PhotoDatabase.getDatabase(this) }
    val appPreferences: AppPreferences by lazy { AppPreferences(this) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}
