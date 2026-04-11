package com.axion.tempus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NotesRepository private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "notes.json")

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        synchronized(this@NotesRepository) {
            if (loaded) return@withContext
            if (!file.exists()) {
                val note = Note(id = 1L, title = "", body = "")
                _notes.value = listOf(note)
                writeFileLocked()
                loaded = true
                return@withContext
            }
            val text = file.readText()
            if (text.isBlank()) {
                val note = Note(id = 1L, title = "", body = "")
                _notes.value = listOf(note)
                writeFileLocked()
                loaded = true
                return@withContext
            }
            val arr = JSONArray(text)
            val list = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Note(
                    id = o.getLong("id"),
                    title = o.optString("title", ""),
                    body = o.optString("body", ""),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
            _notes.value = list.sortedByDescending { it.updatedAt }
            loaded = true
        }
    }

    fun updateNotes(transform: (List<Note>) -> List<Note>) {
        _notes.update(transform)
    }

    suspend fun persist() = withContext(Dispatchers.IO) {
        writeFileLocked()
    }

    private fun writeFileLocked() {
        val arr = JSONArray()
        _notes.value.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("body", n.body)
            o.put("updatedAt", n.updatedAt)
            arr.put(o)
        }
        file.writeText(arr.toString())
    }

    companion object {
        @Volatile
        private var instance: NotesRepository? = null

        fun get(context: Context): NotesRepository {
            return instance ?: synchronized(this) {
                instance ?: NotesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
