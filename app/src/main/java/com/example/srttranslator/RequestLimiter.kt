package com.example.srttranslator

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Allows at most four request starts in every rolling 60-second window. */
class RequestLimiter(private val maxRequests: Int = 4) {
    private val mutex = Mutex()
    private val starts = ArrayDeque<Long>()
    suspend fun awaitSlot() {
        while (true) {
            val wait = mutex.withLock {
                val now = System.currentTimeMillis(); while (starts.firstOrNull()?.let { now - it >= 60_000 } == true) starts.removeFirst()
                if (starts.size < maxRequests) { starts.addLast(now); 0L } else 60_000L - (now - starts.first()) + 25L
            }
            if (wait <= 0) return
            delay(wait)
        }
    }
}
