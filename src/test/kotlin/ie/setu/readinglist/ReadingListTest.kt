package ie.setu.readinglist

import ie.setu.readinglist.domain.Book
import ie.setu.readinglist.domain.DomainError
import ie.setu.readinglist.domain.ReadingList
import ie.setu.readinglist.domain.ReadingStatus
import ie.setu.readinglist.domain.SortOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReadingListTest {

    private val fixedToday: LocalDate = LocalDate.of(2026, 3, 4)

    private fun list(vararg books: Book): ReadingList = ReadingList(books.toList()) { fixedToday }

    private fun book(
        id: String,
        title: String = "Title $id",
        author: String = "Author $id",
        status: ReadingStatus = ReadingStatus.TO_READ,
        rating: Int? = null,
        addedOn: LocalDate = fixedToday,
        finishedOn: LocalDate? = null,
    ): Book = Book(
        id = id,
        title = title,
        author = author,
        status = status,
        rating = rating,
        addedOn = addedOn,
        finishedOn = finishedOn,
    )

    @Test
    fun `add assigns sequential ids and trims input`() {
        val readingList = ReadingList(today = { fixedToday })

        val first = readingList.add("  Dune  ", "  Frank Herbert  ")
        val second = readingList.add("Neuromancer", "William Gibson")

        assertEquals("b1", first.id)
        assertEquals("Dune", first.title)
        assertEquals("Frank Herbert", first.author)
        assertEquals("b2", second.id)
        assertEquals(ReadingStatus.TO_READ, first.status)
        assertEquals(fixedToday, first.addedOn)
    }

    @Test
    fun `add rejects blank title and blank author`() {
        val readingList = ReadingList(today = { fixedToday })

        assertThrows(DomainError.BlankTitle::class.java) { readingList.add("   ", "Someone") }
        assertThrows(DomainError.BlankAuthor::class.java) { readingList.add("Something", " ") }
    }

    @Test
    fun `add rejects a duplicate title and author ignoring case`() {
        val readingList = list(book("b1", title = "Dune", author = "Frank Herbert"))

        val error = assertThrows(DomainError.Duplicate::class.java) {
            readingList.add("dune", "frank herbert")
        }
        assertEquals("dune", error.title)
    }

    @Test
    fun `add rejects ratings outside the allowed range`() {
        val readingList = ReadingList(today = { fixedToday })

        assertThrows(DomainError.InvalidRating::class.java) {
            readingList.add("Dune", "Frank Herbert", ReadingStatus.FINISHED, rating = 0)
        }
        assertThrows(DomainError.InvalidRating::class.java) {
            readingList.add("Dune", "Frank Herbert", ReadingStatus.FINISHED, rating = 6)
        }
    }

    @Test
    fun `add rejects a rating for a book that is not finished`() {
        val readingList = ReadingList(today = { fixedToday })

        assertThrows(DomainError.RatingNeedsFinished::class.java) {
            readingList.add("Dune", "Frank Herbert", ReadingStatus.TO_READ, rating = 4)
        }
    }

    @Test
    fun `add normalises tags by trimming deduplicating and sorting`() {
        val readingList = ReadingList(today = { fixedToday })

        val added = readingList.add("Dune", "Frank Herbert", tags = listOf(" sci-fi", "classic", "sci-fi", " "))

        assertEquals(listOf("classic", "sci-fi"), added.tags)
    }

    @Test
    fun `start moves a to-read book to reading`() {
        val readingList = list(book("b1"))

        val started = readingList.start("B1")

        assertEquals(ReadingStatus.READING, started.status)
    }

    @Test
    fun `start rejects a book that is not in the to-read state`() {
        val readingList = list(book("b1", status = ReadingStatus.FINISHED, rating = 4))

        assertThrows(DomainError.InvalidTransition::class.java) { readingList.start("b1") }
    }

    @Test
    fun `finish records the date and an optional rating`() {
        val readingList = list(book("b1", status = ReadingStatus.READING))

        val finished = readingList.finish("b1", 5)

        assertEquals(ReadingStatus.FINISHED, finished.status)
        assertEquals(5, finished.rating)
        assertEquals(fixedToday, finished.finishedOn)
    }

    @Test
    fun `finish rejects a book that is already finished`() {
        val readingList = list(book("b1", status = ReadingStatus.FINISHED, rating = 3, finishedOn = fixedToday))

        assertThrows(DomainError.InvalidTransition::class.java) { readingList.finish("b1") }
    }

    @Test
    fun `rate only applies to finished books`() {
        val readingList = list(book("b1", status = ReadingStatus.READING))

        assertThrows(DomainError.RatingNeedsFinished::class.java) { readingList.rate("b1", 4) }
    }

    @Test
    fun `rate updates the rating of a finished book`() {
        val readingList = list(book("b1", status = ReadingStatus.FINISHED, rating = 2, finishedOn = fixedToday))

        val rated = readingList.rate("b1", 4)

        assertEquals(4, rated.rating)
    }

    @Test
    fun `missing ids raise NotFound`() {
        val readingList = list(book("b1"))

        assertThrows(DomainError.NotFound::class.java) { readingList.start("b99") }
        assertNull(readingList.find("b99"))
    }

    @Test
    fun `remove deletes the book and frees its id`() {
        val readingList = list(book("b1"), book("b2"))

        val removed = readingList.remove("b1")

        assertEquals("b1", removed.id)
        assertEquals(1, readingList.size())
        assertNull(readingList.find("b1"))
        assertEquals("b3", readingList.add("New", "Writer").id)
    }

    @Test
    fun `filter by status keeps insertion order`() {
        val readingList = list(
            book("b1", status = ReadingStatus.TO_READ),
            book("b2", status = ReadingStatus.READING),
            book("b3", status = ReadingStatus.TO_READ),
        )

        val toRead = readingList.filter(status = ReadingStatus.TO_READ)

        assertEquals(listOf("b1", "b3"), toRead.map { it.id })
    }

    @Test
    fun `filter by author matches case insensitive substrings`() {
        val readingList = list(
            book("b1", author = "Jane Austen"),
            book("b2", author = "Ursula K. Le Guin"),
            book("b3", author = "Jane Austen"),
        )

        val austen = readingList.filter(author = "austen")

        assertEquals(listOf("b1", "b3"), austen.map { it.id })
        assertEquals(listOf("Ursula K. Le Guin"), readingList.authors().filter { it.contains("Guin") })
    }

    @Test
    fun `filter with sort order returns titles alphabetically`() {
        val readingList = list(
            book("b1", title = "Zebra"),
            book("b2", title = "Apple"),
            book("b3", title = "Mango"),
        )

        val titles = readingList.filter(sort = SortOrder.TITLE).map { it.title }

        assertEquals(listOf("Apple", "Mango", "Zebra"), titles)
    }

    @Test
    fun `filter by rating puts unrated books last`() {
        val readingList = list(
            book("b1", status = ReadingStatus.FINISHED, rating = 2, finishedOn = fixedToday),
            book("b2", status = ReadingStatus.READING),
            book("b3", status = ReadingStatus.FINISHED, rating = 5, finishedOn = fixedToday),
        )

        val ordered = readingList.filter(sort = SortOrder.RATING).map { it.id }

        assertEquals("b3", ordered.first())
        assertEquals("b2", ordered.last())
    }

    @Test
    fun `nextUp returns the oldest unread books first`() {
        val readingList = list(
            book("b2", addedOn = LocalDate.of(2026, 1, 2)),
            book("b1", addedOn = LocalDate.of(2026, 1, 1)),
            book("b3", status = ReadingStatus.READING, addedOn = LocalDate.of(2026, 1, 3)),
            book("b4", addedOn = LocalDate.of(2026, 1, 4)),
        )

        val next = readingList.nextUp(2)

        assertEquals(listOf("b1", "b2"), next.map { it.id })
    }

    @Test
    fun `starting a book removes it from the next up queue`() {
        val readingList = list(book("b1"), book("b2"))

        readingList.start("b1")

        assertEquals(listOf("b2"), readingList.nextUp(5).map { it.id })
    }

    @Test
    fun `stats counts each status and averages finished ratings`() {
        val readingList = list(
            book("b1", status = ReadingStatus.TO_READ),
            book("b2", status = ReadingStatus.READING),
            book("b3", status = ReadingStatus.FINISHED, rating = 4, finishedOn = fixedToday),
            book("b4", status = ReadingStatus.FINISHED, rating = 2, finishedOn = fixedToday),
        )

        val stats = readingList.stats()

        assertEquals(4, stats.total)
        assertEquals(1, stats.byStatus[ReadingStatus.TO_READ])
        assertEquals(1, stats.byStatus[ReadingStatus.READING])
        assertEquals(2, stats.byStatus[ReadingStatus.FINISHED])
        assertEquals(3.0, stats.averageRating)
    }

    @Test
    fun `stats reports no average when nothing is rated`() {
        val readingList = list(book("b1"))

        assertNull(readingList.stats().averageRating)
    }

    @Test
    fun `nextUp requires a positive limit`() {
        val readingList = list(book("b1"))

        assertThrows(IllegalArgumentException::class.java) { readingList.nextUp(0) }
        assertTrue(readingList.nextUp(1).isNotEmpty())
    }
}
