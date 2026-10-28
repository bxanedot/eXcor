package com.excor

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding

@OptIn(ExperimentalForeignApi::class)
internal actual fun fixture(name: String): String =
    NSString.create(stringWithContentsOfFile = "$FIXTURES/$name", encoding = NSUTF8StringEncoding, error = null)
        ?.toString() ?: error("Missing fixture $name")
