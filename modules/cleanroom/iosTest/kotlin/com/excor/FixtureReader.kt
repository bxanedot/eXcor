package com.excor

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun fixture(name: String): String =
    NSString.create(contentsOfFile = "$FIXTURES/$name", encoding = NSUTF8StringEncoding, error = null)
        ?.toString() ?: error("Missing fixture $name")
