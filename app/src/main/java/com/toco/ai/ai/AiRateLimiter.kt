package com.toco.ai.ai

import java.util.ArrayDeque
import kotlin.math.max

/**
 * Lightweight, in-process rate limiting for AI requests.
 *
 * This prevents accidental request floods, repeated wake-word triggers, and
 * rapid UI taps from burning API quota. It is intentionally local to the app;
 * a backend proxy is still required if API keys need strong server-side abuse
 * protection.
 */
internal object AiRateLimiter {
    private const val MIN_INTERVAL_MS = 1_500L
    private const val MINUTE_WINDOW_MS = 60_000L
    private const val HOUR_WINDOW_MS = 60 * 60_000L

    private const val MAX_REQUESTS_PER_MINUTE = 12
    private const val MAX_REQUESTS_PER_HOUR = 120

    private val acceptedRequests = ArrayDeque<Long>()

    /**
     * Returns null when the request is allowed, otherwise the number of
     * milliseconds the caller should wait before retrying.
     */
    @Synchronized
    fun retryAfterMillis(nowMs: Long = monotonicMillis()): Long? {
        prune(nowMs)

        val last = acceptedRequests.peekLast()
        if (last != null) {
            val remaining = MIN_INTERVAL_MS - (nowMs - last)
            if (remaining > 0) return remaining
        }

        val minuteCutoff = nowMs - MINUTE_WINDOW_MS
        val minuteRequests = acceptedRequests.filter { it > minuteCutoff }
        if (minuteRequests.size >= MAX_REQUESTS_PER_MINUTE) {
            val oldestInMinute = minuteRequests.first()
            return max(1L, MINUTE_WINDOW_MS - (nowMs - oldestInMinute))
        }

        if (acceptedRequests.size >= MAX_REQUESTS_PER_HOUR) {
            val oldest = acceptedRequests.peekFirst()
            return max(1L, HOUR_WINDOW_MS - (nowMs - oldest))
        }

        acceptedRequests.addLast(nowMs)
        return null
    }

    @Synchronized
    fun reset() = acceptedRequests.clear()

    private fun prune(nowMs: Long) {
        val cutoff = nowMs - HOUR_WINDOW_MS
        while (acceptedRequests.isNotEmpty() && acceptedRequests.peekFirst() <= cutoff) {
            acceptedRequests.removeFirst()
        }
    }

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
}
