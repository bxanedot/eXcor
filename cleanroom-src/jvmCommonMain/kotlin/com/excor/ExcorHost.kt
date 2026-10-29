package com.excor

import com.excor.internal.primary.extraction.TokenProvider
import java.io.File
import okhttp3.OkHttpClient

interface ExcorHost : ExcorHostServices {
    val tokenProvider: TokenProvider?
    val filesDir: File?
    val httpClient: OkHttpClient
}
