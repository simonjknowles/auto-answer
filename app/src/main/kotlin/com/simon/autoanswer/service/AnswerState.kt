package com.simon.autoanswer.service

import java.util.concurrent.atomic.AtomicLong

object AnswerState {
    private val armedAt = AtomicLong(0L)
    private val minFireAt = AtomicLong(0L)

    fun arm(minFireAtMs: Long = 0L) {
        armedAt.set(System.currentTimeMillis())
        minFireAt.set(minFireAtMs)
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

    fun minFireAtMs(): Long = minFireAt.get()

    fun clear() {
        armedAt.set(0L)
        minFireAt.set(0L)
    }
}
