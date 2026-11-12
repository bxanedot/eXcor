package com.excor

import com.excor.internal.primary.extraction.TokenProvider

interface ExcorHost : ExcorHostServices {
    val tokenProvider: TokenProvider?
}
