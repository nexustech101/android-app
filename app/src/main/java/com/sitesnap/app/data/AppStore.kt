package com.sitesnap.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Small JSON-file store for jobs, settings and upload bookkeeping. Photos themselves live in MediaStore. */
class AppStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun load(): AppState =
        runCatching { json.decodeFromString(AppState.serializer(), file.readText()) }.getOrDefault(AppState())

    suspend fun update(transform: (AppState) -> AppState): AppState = mutex.withLock {
        val next = transform(_state.value)
        _state.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(AppState.serializer(), next))
            tmp.renameTo(file)
        }
        next
    }
}
