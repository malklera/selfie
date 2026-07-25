package com.selfie

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import android.os.Build
import com.selfie.data.gallery.GalleryRepository
import com.selfie.data.preferences.PreferencesRepository

class SelfieApp : Application(), SingletonImageLoader.Factory {
    
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(this)
    }
    
    val galleryRepository: GalleryRepository by lazy {
        GalleryRepository(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
