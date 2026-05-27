package com.kawa.energy.counter.data.history

import com.kawa.energy.counter.domain.SessionStore
import com.kawa.energy.counter.domain.SessionSample

import com.kawa.energy.counter.data.monitor.AndroidContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

actual fun createSessionStore(): SessionStore {
    val ctx = AndroidContextHolder.require()
    val file = File(ctx.filesDir, "sessions.ndjson")
    return FileSessionStore(file)
}

private class FileSessionStore(private val file: File) : SessionStore {

    private val mutex = Mutex()

    override suspend fun append(sample: SessionSample) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.appendText(Json.encodeToString(SessionSample.serializer(), sample) + "\n")
        }
    }

    override suspend fun loadAll(): List<SessionSample> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock emptyList()
            file.useLines { lines ->
                lines.mapNotNull { l ->
                    runCatching { Json.decodeFromString(SessionSample.serializer(), l) }.getOrNull()
                }.toList()
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) file.writeText("")
        }
    }
}
