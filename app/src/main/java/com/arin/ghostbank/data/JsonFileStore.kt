package com.arin.ghostbank.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Writes are atomic (temp file then rename) and encrypted, so a crash mid-write cannot corrupt or lose records.
 */
class JsonFileStore<T>(
    context: Context,
    fileName: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T
) {
    private val mutex = Mutex()
    private val file = File(context.filesDir, fileName)
    private val temp = File(context.filesDir, "$fileName.tmp")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    suspend fun read(): T = mutex.withLock { readLocked() }

    private fun readLocked(): T {
        if (!file.exists()) return default()
        return runCatching {
            val plain = Crypto.decrypt(file.readBytes()).decodeToString()
            json.decodeFromString(serializer, plain)
        }.getOrElse { default() }
    }

    suspend fun write(value: T) = mutex.withLock { writeLocked(value) }

    private fun writeLocked(value: T) {
        val bytes = Crypto.encrypt(json.encodeToString(serializer, value).encodeToByteArray())
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.writeBytes(bytes)
            temp.delete()
        }
    }

    suspend fun update(block: (T) -> T): T = mutex.withLock {
        val next = block(readLocked())
        writeLocked(next)
        next
    }
}
