package moe.kabii

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val LOG: Logger = LoggerFactory.getLogger("moe.kabii")

// global okhttp instance
val OkHTTP = OkHttpClient()

// json parser instance
val MOSHI: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

val JSON = "application/json; charset=UTF-8".toMediaType()
val UNENCODED = "application/x-www-form-urlencoded".toMediaType()