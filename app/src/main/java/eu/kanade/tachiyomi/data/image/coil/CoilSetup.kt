package eu.kanade.tachiyomi.data.image.coil

import android.content.Context
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.OkHttpClient

class CoilSetup(context: Context) {
    init {
        val imageLoader = ImageLoader.Builder(context)
            .availableMemoryPercentage(0.40)
            .crossfade(true)
            .allowRgb565(true)
            .allowHardware(false)
            .componentRegistry {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder())
                } else {
                    add(GifDecoder())
                }
                add(SvgDecoder(context))
                add(MangaFetcher())
                add(ByteArrayFetcher())
            }.okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(ChuckerInterceptor(context))
                    .build()
            }
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
