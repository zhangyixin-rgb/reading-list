package ie.setu.readinglist

import ie.setu.readinglist.domain.Book
import ie.setu.readinglist.domain.ReadingStatus
import ie.setu.readinglist.storage.JsonFileRepository
import ie.setu.readinglist.storage.StorageError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class JsonFileRepositoryTest {

    @TempDir
    lateinit var directory: Path

    private fun sample(id: String = "b1"): Book = Book(
        id = id,
        title = "Dune",
        author = "Frank Herbert",
        status = ReadingStatus.FINISHED,
        rating = 5,
        addedOn = LocalDate.of(2026, 1, 1),
        finishedOn = LocalDate.of(2026, 2, 1),
        tags = listOf("sci-fi"),
    )

    @Test
    fun `load returns an empty list when the file does not exist`() {
        val repository = JsonFileRepository(directory.resolve("library.json"))

        assertTrue(repository.load().isEmpty())
    }

    @Test
    fun `save then load round-trips every field`() {
        val file = directory.resolve("nested").resolve("library.json")
        val repository = JsonFileRepository(file)

        repository.save(listOf(sample()))

        assertTrue(Files.exists(file))
        assertEquals(listOf(sample()), repository.load())
    }

    @Test
    fun `save replaces the previous contents`() {
        val repository = JsonFileRepository(directory.resolve("library.json"))

        repository.save(listOf(sample("b1"), sample("b2")))
        repository.save(listOf(sample("b3")))

        assertEquals(listOf("b3"), repository.load().map { it.id })
    }

    @Test
    fun `save does not leave temporary files behind`() {
        val repository = JsonFileRepository(directory.resolve("library.json"))

        repository.save(listOf(sample()))

        val siblings = Files.list(directory).use { stream -> stream.toList() }
        assertEquals(1, siblings.size)
        assertFalse(siblings.first().fileName.toString().endsWith(".tmp"))
    }

    @Test
    fun `an empty file is treated as an empty library`() {
        val file = directory.resolve("library.json")
        Files.writeString(file, "   ")

        assertTrue(JsonFileRepository(file).load().isEmpty())
    }

    @Test
    fun `a corrupt file is reported instead of silently discarded`() {
        val file = directory.resolve("library.json")
        Files.writeString(file, "{ this is not json")

        val error = assertThrows(StorageError::class.java) { JsonFileRepository(file).load() }

        assertTrue(error.message!!.contains("library.json"))
    }

    @Test
    fun `unknown fields are ignored so old and new versions can coexist`() {
        val file = directory.resolve("library.json")
        Files.writeString(
            file,
            """
            {
              "version": 1,
              "books": [
                {
                  "id": "b1",
                  "title": "Dune",
                  "author": "Frank Herbert",
                  "status": "TO_READ",
                  "addedOn": "2026-01-01",
                  "futureField": "ignored"
                }
              ]
            }
            """.trimIndent(),
        )

        val loaded = JsonFileRepository(file).load()

        assertEquals("b1", loaded.single().id)
    }
}
