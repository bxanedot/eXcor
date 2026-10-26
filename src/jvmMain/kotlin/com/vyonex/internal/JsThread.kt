package com.vyonex.internal

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

internal actual suspend fun <T> onJsThread(block: suspend () -> T): T = suspendCancellableCoroutine { continuation ->
    Thread(null, { continuation.resumeWith(runCatching { runBlocking { block() } }) }, "vyonex-js", JS_THREAD_STACK_BYTES)
        .apply { isDaemon = true }
        .start()
}
