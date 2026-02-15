package com.stashortrash.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.stashortrash.app.data.AppPreferences
import com.stashortrash.app.data.BillingManager
import com.stashortrash.app.data.PhotoDatabase

class PhotoCleanupApp : Application(), ImageLoaderFactory {
    val database: PhotoDatabase by lazy { PhotoDatabase.getDatabase(this) }
    val appPreferences: AppPreferences by lazy { AppPreferences(this) }
    val billingManager: BillingManager by lazy { BillingManager(this, appPreferences) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}
