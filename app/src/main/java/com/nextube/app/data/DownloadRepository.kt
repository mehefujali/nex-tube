package com.nextube.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/** Single source of truth for the download list, persisted as JSON in app storage. */
object DownloadRepository {
    private lateinit var file: File
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun init(context: Context) {
        file = File(context.filesDir, "downloads.json")
        val loaded = runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { DownloadTask.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        // Anything that was mid-flight when the app died can't resume on its own.
        _tasks.value = loaded.map {
            if (it.status.isActive) it.copy(status = DownloadStatus.FAILED, error = "Interrupted — tap retry") else it
        }
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun add(task: DownloadTask) {
        _tasks.update { listOf(task) + it }
        persist()
    }

    fun addAll(newTasks: List<DownloadTask>) {
        if (newTasks.isEmpty()) return
        _tasks.update { newTasks + it }
        persist()
    }

    /** [persist] = false for high-frequency progress ticks. */
    fun update(id: String, persist: Boolean = true, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
        if (persist) persist()
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun removeAll(ids: Set<String>) {
        if (ids.isEmpty()) return
        _tasks.update { list -> list.filterNot { it.id in ids } }
        persist()
    }

    private fun persist() {
        val snapshot = _tasks.value
        io.launch {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { arr.put(it.toJson()) }
                val tmp = File(file.parentFile, "downloads.json.tmp")
                tmp.writeText(arr.toString())
                if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true).also { tmp.delete() }
            }
        }
    }
}
