package com.kawa.energy.counter.history

interface SessionStore {
    suspend fun append(sample: SessionSample)
    suspend fun loadAll(): List<SessionSample>
    suspend fun clear()
}

expect fun createSessionStore(): SessionStore
