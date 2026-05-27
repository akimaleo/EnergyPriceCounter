package com.kawa.energy.counter.data.history

import com.kawa.energy.counter.domain.SessionStore
import com.kawa.energy.counter.domain.SessionSample

actual fun createSessionStore(): SessionStore = InMemorySessionStore()

private class InMemorySessionStore : SessionStore {
    private val items = mutableListOf<SessionSample>()
    override suspend fun append(sample: SessionSample) { items += sample }
    override suspend fun loadAll(): List<SessionSample> = items.toList()
    override suspend fun clear() { items.clear() }
}
