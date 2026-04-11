package com.axion.tempus.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.axion.tempus.data.Note
import com.axion.tempus.data.NotesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NotesRepository.get(application)

    private var saveJob: Job? = null

    private val _currentId = MutableStateFlow<Long?>(null)
    val currentId: StateFlow<Long?> = _currentId.asStateFlow()

    val allNotes: StateFlow<List<Note>> = repo.notes

    val currentNote: StateFlow<Note?> = combine(repo.notes, _currentId) { notes, id ->
        id?.let { wanted -> notes.find { it.id == wanted } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repo.ensureLoaded()
            val list = repo.notes.value
            val pick = list.maxByOrNull { it.updatedAt } ?: list.firstOrNull()
            _currentId.value = pick?.id
        }
    }

    fun selectNote(id: Long) {
        _currentId.value = id
    }

    fun createNote() {
        viewModelScope.launch {
            val newId = (repo.notes.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val newNote = Note(id = newId, title = "", body = "")
            repo.updateNotes { it + newNote }
            repo.persist()
            _currentId.value = newNote.id
        }
    }

    fun scheduleSave(title: String, body: String) {
        val id = _currentId.value ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(450)
            repo.updateNotes { list ->
                list.map { n ->
                    if (n.id == id) {
                        n.copy(
                            title = title,
                            body = body,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        n
                    }
                }
            }
            repo.persist()
        }
    }
}
