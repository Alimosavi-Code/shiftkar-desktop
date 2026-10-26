package ir.shiftkar.neo.data

import ir.shiftkar.neo.platform.currentTimeMillis
import ir.shiftkar.neo.platform.readAppStorageText
import ir.shiftkar.neo.platform.writeAppStorageText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Notes persistence — multiplatform offline local storage (Android, iOS, Desktop).
 * Preserves the exact same NoteEntity schema and NotesRepository contract
 * (observeAll, listAll, listMonth, get, save where empty text deletes).
 */

@Serializable
data class NoteEntity(
    val dateKey: String,   // "1405-05-04"
    val text: String,
    val updatedAt: Long
)

object NotesStore {
    private const val FILE_NAME = "shiftkar_notes_v1.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private fun loadFromDisk(): List<NoteEntity> {
        val raw = runCatching { readAppStorageText(FILE_NAME) }.getOrNull() ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(NoteEntity.serializer()), raw)
                .sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    private fun saveToDisk(notes: List<NoteEntity>) {
        runCatching {
            writeAppStorageText(
                FILE_NAME,
                json.encodeToString(ListSerializer(NoteEntity.serializer()), notes)
            )
        }
    }

    private val _notes = MutableStateFlow(loadFromDisk())
    val notesFlow: Flow<List<NoteEntity>> = _notes.asStateFlow()

    fun reload() {
        _notes.value = loadFromDisk()
    }

    suspend fun listAll(): List<NoteEntity> = _notes.value

    suspend fun listMonth(jy: Int, jm: Int): List<NoteEntity> {
        val mm = jm.toString().padStart(2, '0')
        val from = "$jy-$mm-01"
        val to = "$jy-$mm-31"
        return _notes.value.filter { it.dateKey in from..to }.sortedByDescending { it.dateKey }
    }

    suspend fun get(dateKey: String): NoteEntity? =
        _notes.value.firstOrNull { it.dateKey == dateKey }

    suspend fun save(dateKey: String, text: String) {
        mutex.withLock {
            val trimmed = text.trim()
            val updated = if (trimmed.isEmpty()) {
                _notes.value.filterNot { it.dateKey == dateKey }
            } else {
                val entry = NoteEntity(dateKey = dateKey, text = text, updatedAt = currentTimeMillis())
                (_notes.value.filterNot { it.dateKey == dateKey } + entry).sortedByDescending { it.updatedAt }
            }
            _notes.value = updated
            withContext(Dispatchers.Default) {
                saveToDisk(updated)
            }
        }
    }
}

class NotesRepository {
    fun observeAll(): Flow<List<NoteEntity>> = NotesStore.notesFlow
    suspend fun listAll(): List<NoteEntity> = NotesStore.listAll()
    suspend fun listMonth(jy: Int, jm: Int): List<NoteEntity> = NotesStore.listMonth(jy, jm)
    suspend fun get(dateKey: String): NoteEntity? = NotesStore.get(dateKey)
    suspend fun save(dateKey: String, text: String) = NotesStore.save(dateKey, text)
}
