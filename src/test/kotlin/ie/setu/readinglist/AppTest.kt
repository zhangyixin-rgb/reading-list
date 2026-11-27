package ie.setu.readinglist

import ie.setu.readinglist.cli.App
import ie.setu.readinglist.cli.ExitCode
import ie.setu.readinglist.domain.Book
import ie.setu.readinglist.domain.ReadingStatus
import ie.setu.readinglist.storage.JsonFileRepository
import ie.setu.readinglist.storage.ReadingListRepository
import ie.setu.readinglist.storage.StorageError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDate

class AppTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 4)
    private val out = StringBuilder()
    private val err = StringBuilder()

    private fun app(repository: ReadingListRepository): App =
        App(repository = repository, out = out, err = err, clock = { today })

    private fun run(app: App, vararg args: String): Int = app.run(arrayOf(*args))

    private fun sample(id: String = "b1", status: ReadingStatus = ReadingStatus.TO_READ, rating: Int? = null) = Book(
        id = id,
        title = "Dune",
        author = "Frank Herbert",
        status = status,
        rating = rating,
        addedOn = LocalDate.of(2026, 1, 1),
        finishedOn = if (status == ReadingStatus.FINISHED) LocalDate.of(2026, 2, 1) else null,
    )

    @Test
    fun `add stores the book and reports its id`() {
        val repository = InMemoryRepository()

        val code = run(app(repository), "add", "--title", "Dune", "--author", "Frank Herbert")

        assertEquals(ExitCode.SUCCESS, code)
        assertEquals(1, repository.books.size)
        assertEquals("b1", repository.books.single().id)
        assertTrue(out.contains("Added b1: Dune by Frank Herbert"))
    }

    @Test
    fun `list prints a table and honours filters`() {
        val repository = InMemoryRepository(
            listOf(
                sample("b1", ReadingStatus.TO_READ),
                sample("b2", ReadingStatus.FINISHED, rating = 4),
            ),
        )

        val code = run(app(repository), "list", "--status", "finished")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(out.contains("b2"), out.toString())
        assertTrue(!out.contains("b1"), out.toString())
        assertTrue(out.contains("Dune"))
    }

    @Test
    fun `list with no matches explains that there is nothing to show`() {
        val code = run(app(InMemoryRepository()), "list")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(out.contains("No books to show"))
    }

    @Test
    fun `show prints the details of one book`() {
        val repository = InMemoryRepository(listOf(sample()))

        val code = run(app(repository), "show", "b1")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(out.contains("title:    Dune"))
        assertTrue(out.contains("finished: -"))
    }

    @Test
    fun `an unknown id returns the not found exit code`() {
        val code = run(app(InMemoryRepository()), "show", "b9")

        assertEquals(ExitCode.NOT_FOUND, code)
        assertTrue(err.contains("No book with id 'b9'"))
    }

    @Test
    fun `a usage error returns exit code two`() {
        val code = run(app(InMemoryRepository()), "add", "--title", "Dune")

        assertEquals(ExitCode.USAGE, code)
        assertTrue(err.contains("--author"))
        assertTrue(err.contains("reading-list help"))
    }

    @Test
    fun `a rejected request returns the validation exit code`() {
        val code = run(app(InMemoryRepository()), "add", "--title", " ", "--author", "Someone")

        assertEquals(ExitCode.VALIDATION, code)
        assertTrue(err.contains("Title must not be blank"))
    }

    @Test
    fun `a storage failure returns the storage exit code`() {
        val code = run(app(BrokenRepository()), "list")

        assertEquals(ExitCode.STORAGE, code)
        assertTrue(err.contains("disk unavailable"))
    }

    @Test
    fun `finish moves a book and saves the rating`() {
        val repository = InMemoryRepository(listOf(sample(status = ReadingStatus.READING)))

        val code = run(app(repository), "finish", "b1", "--rating", "5")

        assertEquals(ExitCode.SUCCESS, code)
        assertEquals(ReadingStatus.FINISHED, repository.books.single().status)
        assertEquals(5, repository.books.single().rating)
        assertTrue(out.contains("Finished b1"))
    }

    @Test
    fun `rate changes the score of a finished book`() {
        val repository = InMemoryRepository(listOf(sample(status = ReadingStatus.FINISHED, rating = 2)))

        val code = run(app(repository), "rate", "b1", "--rating", "4")

        assertEquals(ExitCode.SUCCESS, code)
        assertEquals(4, repository.books.single().rating)
        assertTrue(out.contains("Rated b1"))
    }

    @Test
    fun `remove deletes a book`() {
        val repository = InMemoryRepository(listOf(sample()))

        val code = run(app(repository), "remove", "b1")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(repository.books.isEmpty())
        assertTrue(out.contains("Removed b1"))
    }

    @Test
    fun `next prints the unread queue`() {
        val repository = InMemoryRepository(listOf(sample("b1"), sample("b2")))

        val code = run(app(repository), "next")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(out.contains("b1"))
        assertTrue(out.contains("b2"))
    }

    @Test
    fun `next explains when the queue is empty`() {
        val code = run(app(InMemoryRepository()), "next")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(out.contains("Nothing left to read"))
    }

    @Test
    fun `stats prints totals and the average rating`() {
        val repository = InMemoryRepository(
            listOf(
                sample("b1", ReadingStatus.TO_READ),
                sample("b2", ReadingStatus.FINISHED, rating = 4),
            ),
        )

        val code = run(app(repository), "stats")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(out.contains("Total books: 2"))
        assertTrue(out.contains("finished  1"))
        assertTrue(out.contains("Average rating: 4.00"))
    }

    @Test
    fun `stats reports when nothing has been rated`() {
        run(app(InMemoryRepository(listOf(sample()))), "stats")

        assertTrue(out.contains("Average rating: not rated yet"))
    }

    @Test
    fun `help prints usage and succeeds`() {
        val code = run(app(InMemoryRepository()), "help")

        assertEquals(ExitCode.SUCCESS, code)
        assertTrue(out.contains("Usage:"))
        assertTrue(out.contains("reading-list <command> [options]"))
    }

    @Test
    fun `a full session persists across separate runs`(@TempDir directory: Path) {
        val file = directory.resolve("library.json")

        run(app(JsonFileRepository(file)), "add", "--title", "Dune", "--author", "Frank Herbert")
        run(app(JsonFileRepository(file)), "start", "b1")
        val code = run(app(JsonFileRepository(file)), "finish", "b1", "--rating", "5")

        assertEquals(ExitCode.SUCCESS, code)
        val stored = JsonFileRepository(file).load().single()
        assertEquals(ReadingStatus.FINISHED, stored.status)
        assertEquals(5, stored.rating)
        assertEquals(today, stored.finishedOn)
    }

    private class InMemoryRepository(var books: List<Book> = emptyList()) : ReadingListRepository {
        override fun load(): List<Book> = books

        override fun save(books: List<Book>) {
            this.books = books
        }
    }

    private class BrokenRepository : ReadingListRepository {
        override fun load(): List<Book> = throw StorageError("disk unavailable")

        override fun save(books: List<Book>) = throw StorageError("disk unavailable")
    }
}
