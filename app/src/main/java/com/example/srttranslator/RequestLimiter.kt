package com.example.srttranslator

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the first request for each of the 3 parts from being blocked by retries. */
class RequestLimiter(private val maxRequests: Int = 4, private val reservedInitialRequests: Int = 3) {
    private val mutex = Mutex()
    private val starts = ArrayDeque<Long>()
    private var initialStarted = 0

    suspend fun awaitSlot(isInitialRequest: Boolean) {
        while (true) {
            val wait = mutex.withLock {
                val now = System.currentTimeMillis()
                while (starts.firstOrNull()?.let { now - it >= 60_000 } == true) starts.removeFirst()
                val initialReservationAvailable = isInitialRequest && initialStarted < reservedInitialRequests
                val retryMustWaitForInitials = !isInitialRequest && initialStarted < reservedInitialRequests
                when {
                    initialReservationAvailable && starts.size < maxRequests -> { initialStarted++; starts.addLast(now); 0L }
                    retryMustWaitForInitials -> 250L
                    starts.size < maxRequests -> { starts.addLast(now); 0L }
                    else -> 60_000L - (now - starts.first()) + 25L
                }
            }
            if (wait <= 0) return
            delay(wait)
        }
    }
}
