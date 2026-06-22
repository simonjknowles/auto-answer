package com.simon.autoanswer.service

import java.util.concurrent.atomic.AtomicLong

object AnswerState {
    private val armedAt = AtomicLong(0L)

    fun arm() {
        armedAt.set(System.currentTimeMillis())
    }

    fun consumeIfFresh(maxAgeMs: Long = 15_000L): Boolean {
        val ts = armedAt.get()
        if (ts == 0L) return false
        if (System.currentTimeMillis() - ts > maxAgeMs) {
            armedAt.set(0L)
            return false
        }
        return armedAt.compareAndSet(ts, 0L)
    }

    fun clear() {
        armedAt.set(0L)
    }
}
