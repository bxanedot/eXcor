package com.bxane.excor.internal

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSThread

internal actual suspend fun <T> onJsThread(block: suspend () -> T): T = suspendCancellableCoroutine { continuation ->
    val thread = NSThread { continuation.resumeWith(runCatching { runBlocking { block() } }) }
    thread.stackSize = JS_THREAD_STACK_BYTES.toULong()
    thread.start()
}
