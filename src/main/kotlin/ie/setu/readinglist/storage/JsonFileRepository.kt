package ie.setu.readinglist.storage

import ie.setu.readinglist.domain.Book
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Stores the reading list as a single JSON document.
 *
 * The file is written to a temporary file in the same directory and then moved
 * over the target, so a crash part-way through a save cannot leave a truncated
 * library behind. A missing file is treated as an empty library; a file that
 * cannot be parsed is reported as a [StorageError] rather than silently
 * discarded, because losing a user's list without telling them is worse than
 * refusing to start.
 */
class JsonFileRepository(private val file: Path) : ReadingListRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun load(): List<Book> {
        if (!Files.exists(file)) return emptyList()
        val text = try {
            Files.readString(file)
        } catch (exception: IOException) {
            throw StorageError("Cannot read ${file.toAbsolutePath()}: ${exception.message}", exception)
        }
        if (text.isBlank()) return emptyList()
        return try {
            json.decodeFromString<LibraryDocument>(text).books
        } catch (exception: SerializationException) {
            throw StorageError("${file.fileName} is not a valid reading-list file: ${exception.message}", exception)
        } catch (exception: IllegalArgumentException) {
            throw StorageError("${file.fileName} contains an invalid value: ${exception.message}", exception)
        }
    }

    override fun save(books: List<Book>) {
        val document = LibraryDocument(books = books)
        val directory = file.toAbsolutePath().parent
        try {
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, "library", ".tmp")
            try {
                Files.writeString(temporary, json.encodeToString(document))
                moveIntoPlace(temporary)
            } finally {
                Files.deleteIfExists(temporary)
            }
        } catch (exception: IOException) {
            throw StorageError("Cannot write ${file.toAbsolutePath()}: ${exception.message}", exception)
        }
    }

    private fun moveIntoPlace(temporary: Path) {
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (exception: AtomicMoveNotSupportedException) {
            // Some file systems cannot move atomically; fall back to a plain replace
            // and keep the original failure attached in case the fallback also fails.
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            } catch (failure: IOException) {
                failure.addSuppressed(exception)
                throw failure
            }
        }
    }
}

@Serializable
internal data class LibraryDocument(
    val version: Int = CURRENT_VERSION,
    val books: List<Book> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}
