package com.stashortrash.app.data

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    /** Master premium toggle — gates all paid features (videos, future IAP features). */
    var isPremium: Boolean
        get() = prefs.getBoolean("premium_unlocked", true)
        set(value) = prefs.edit().putBoolean("premium_unlocked", value).apply()

    /** Videos are a premium feature. */
    val isVideosUnlocked: Boolean get() = isPremium
}
