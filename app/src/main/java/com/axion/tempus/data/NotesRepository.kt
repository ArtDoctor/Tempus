package com.axion.tempus.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

class NotesRepository private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "notes.json")
    private val atomicFile = AtomicFile(file)
    private val writeMutex = Mutex()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    @Volatile
    private var loaded = false
    private var nextId = 1L

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        synchronized(this@NotesRepository) {
            if (loaded) return@withContext
            if (!file.exists()) {
                val note = Note(id = 1L, title = "", body = "")
                _notes.value = listOf(note)
                nextId = 2L
                writeFileLocked(_notes.value)
                loaded = true
                return@withContext
            }
            val text = atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (text.isBlank()) {
                val note = Note(id = 1L, title = "", body = "")
                _notes.value = listOf(note)
                nextId = 2L
                writeFileLocked(_notes.value)
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
            nextId = (_notes.value.maxOfOrNull { it.id } ?: 0L) + 1L
            loaded = true
        }
    }

    suspend fun createNote(): Note = withContext(Dispatchers.IO) {
        ensureLoaded()
        writeMutex.withLock {
            val newNote = Note(id = nextId++, title = "", body = "", updatedAt = System.currentTimeMillis())
            val updatedNotes = listOf(newNote) + _notes.value
            _notes.value = updatedNotes
            writeFileLocked(updatedNotes)
            newNote
        }
    }

    suspend fun deleteAllNotes() = withContext(Dispatchers.IO) {
        ensureLoaded()
        writeMutex.withLock {
            val fresh = Note(id = 1L, title = "", body = "", updatedAt = System.currentTimeMillis())
            _notes.value = listOf(fresh)
            nextId = 2L
            writeFileLocked(_notes.value)
        }
    }

    suspend fun saveNoteText(id: Long, fullText: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        val newlineIndex = fullText.indexOf('\n')
        val title = if (newlineIndex == -1) fullText else fullText.take(newlineIndex)
        val body = if (newlineIndex == -1) "" else fullText.drop(newlineIndex + 1)
        val updatedAt = System.currentTimeMillis()

        writeMutex.withLock {
            val updatedNotes = _notes.value
                .map { note ->
                    if (note.id == id) {
                        note.copy(title = title, body = body, updatedAt = updatedAt)
                    } else {
                        note
                    }
                }
                .sortedByDescending { it.updatedAt }
            _notes.value = updatedNotes
            writeFileLocked(updatedNotes)
        }
    }

    private fun writeFileLocked(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("body", n.body)
            o.put("updatedAt", n.updatedAt)
            arr.put(o)
        }
        val stream = atomicFile.startWrite()
        try {
            stream.write(arr.toString().toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (t: Throwable) {
            atomicFile.failWrite(stream)
            throw t
        }
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
