package com.kawa.energy.counter.history

actual fun createSessionStore(): SessionStore = InMemorySessionStore()

private class InMemorySessionStore : SessionStore {
    private val items = mutableListOf<SessionSample>()
    override suspend fun append(sample: SessionSample) { items += sample }
    override suspend fun loadAll(): List<SessionSample> = items.toList()
    override suspend fun clear() { items.clear() }
}
