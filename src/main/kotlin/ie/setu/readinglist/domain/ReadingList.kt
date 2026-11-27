package ie.setu.readinglist.domain

import java.time.LocalDate
import java.util.TreeMap
import java.util.TreeSet

/** Aggregate figures produced by the `stats` command. */
data class LibraryStats(
    val total: Int,
    val byStatus: Map<ReadingStatus, Int>,
    val averageRating: Double?,
)

/**
 * In-memory aggregate for the reading list.
 *
 * Three indexes are maintained alongside the primary store, and each one has a
 * job rather than existing for decoration:
 *
 *  - `byId` ([LinkedHashMap]) is the primary store. It gives O(1) lookup by id
 *    and keeps insertion order, which is the default order of `list`.
 *  - `byAuthor` ([TreeMap] with a case-insensitive comparator) maps an author to
 *    the ids of their books. It is kept sorted by author name, so `authors()`
 *    and `list --author` never need to sort a list of books.
 *  - `readingQueue` ([TreeSet]) holds only the books still to be read, ordered by
 *    the date they were added. `next` polls its head, which is why it can return
 *    the next N books without scanning or sorting the whole collection.
 */
class ReadingList(
    initial: Collection<Book> = emptyList(),
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val byId = LinkedHashMap<String, Book>()
    private val byAuthor = TreeMap<String, LinkedHashSet<String>>(String.CASE_INSENSITIVE_ORDER)
    private val readingQueue = TreeSet(BOOK_ORDER)

    init {
        initial.forEach(::store)
    }

    /** All books in insertion order. */
    fun books(): List<Book> = byId.values.toList()

    fun size(): Int = byId.size

    fun find(id: String): Book? = byId[normaliseId(id)]

    fun requireBook(id: String): Book = find(id) ?: throw DomainError.NotFound(id)

    /**
     * Adds a book after validating its fields.
     *
     * @throws DomainError.BlankTitle if the title is empty after trimming
     * @throws DomainError.BlankAuthor if the author is empty after trimming
     * @throws DomainError.InvalidRating if the rating is outside 1..5
     * @throws DomainError.RatingNeedsFinished if a rating is supplied for a book that is not finished
     * @throws DomainError.Duplicate if the same title and author are already listed
     */
    fun add(
        title: String,
        author: String,
        status: ReadingStatus = ReadingStatus.TO_READ,
        rating: Int? = null,
        tags: Collection<String> = emptyList(),
    ): Book {
        val cleanTitle = title.trim()
        val cleanAuthor = author.trim()
        if (cleanTitle.isEmpty()) throw DomainError.BlankTitle()
        if (cleanAuthor.isEmpty()) throw DomainError.BlankAuthor()
        validateRating(rating)
        if (rating != null && status != ReadingStatus.FINISHED) throw DomainError.RatingNeedsFinished()
        if (isDuplicate(cleanTitle, cleanAuthor)) throw DomainError.Duplicate(cleanTitle, cleanAuthor)

        val now = today()
        val book = Book(
            id = nextId(),
            title = cleanTitle,
            author = cleanAuthor,
            status = status,
            rating = rating,
            addedOn = now,
            finishedOn = if (status == ReadingStatus.FINISHED) now else null,
            tags = normaliseTags(tags),
        )
        store(book)
        return book
    }

    /** Moves a book from `to-read` to `reading`. */
    fun start(id: String): Book {
        val book = requireBook(id)
        if (book.status != ReadingStatus.TO_READ) {
            throw DomainError.InvalidTransition(book.id, book.status, ReadingStatus.READING)
        }
        return replace(book, book.copy(status = ReadingStatus.READING))
    }

    /** Marks a book as finished, optionally recording a rating at the same time. */
    fun finish(id: String, rating: Int? = null): Book {
        val book = requireBook(id)
        if (book.status == ReadingStatus.FINISHED) {
            throw DomainError.InvalidTransition(book.id, book.status, ReadingStatus.FINISHED)
        }
        validateRating(rating)
        return replace(
            book,
            book.copy(
                status = ReadingStatus.FINISHED,
                rating = rating ?: book.rating,
                finishedOn = today(),
            ),
        )
    }

    /** Re-rates an already finished book. */
    fun rate(id: String, rating: Int): Book {
        val book = requireBook(id)
        if (book.status != ReadingStatus.FINISHED) throw DomainError.RatingNeedsFinished()
        validateRating(rating)
        return replace(book, book.copy(rating = rating))
    }

    fun remove(id: String): Book {
        val book = requireBook(id)
        byId.remove(book.id)
        detachAuthor(book)
        readingQueue.remove(book)
        return book
    }

    /**
     * Returns the books matching the filters, ordered by [sort].
     * An author filter matches case-insensitively on a substring, so `--author austen`
     * finds "Jane Austen".
     */
    fun filter(
        status: ReadingStatus? = null,
        author: String? = null,
        sort: SortOrder = SortOrder.ADDED,
    ): List<Book> {
        val candidates = if (author == null) byId.values else authorMatches(author).mapNotNull(byId::get)
        return candidates.filter { status == null || it.status == status }.sortedWith(sort.comparator)
    }

    /** Author names, sorted case-insensitively by virtue of [byAuthor]. */
    fun authors(): List<String> = byAuthor.keys.toList()

    /** The next [limit] books to read, oldest addition first. */
    fun nextUp(limit: Int): List<Book> {
        require(limit > 0) { "limit must be positive" }
        return readingQueue.take(limit)
    }

    fun stats(): LibraryStats {
        val counts = ReadingStatus.entries.associateWith { status -> byId.values.count { it.status == status } }
        val finishedRatings = byId.values
            .filter { it.status == ReadingStatus.FINISHED }
            .mapNotNull { it.rating }
        return LibraryStats(
            total = byId.size,
            byStatus = counts,
            averageRating = if (finishedRatings.isEmpty()) null else finishedRatings.average(),
        )
    }

    private fun store(book: Book) {
        byId[book.id] = book
        attachAuthor(book)
        if (book.status == ReadingStatus.TO_READ) readingQueue.add(book)
    }

    private fun replace(previous: Book, updated: Book): Book {
        byId[updated.id] = updated
        if (!previous.author.equals(updated.author, ignoreCase = true)) {
            detachAuthor(previous)
            attachAuthor(updated)
        }
        readingQueue.remove(previous)
        if (updated.status == ReadingStatus.TO_READ) readingQueue.add(updated)
        return updated
    }

    private fun attachAuthor(book: Book) {
        byAuthor.getOrPut(book.author) { LinkedHashSet() }.add(book.id)
    }

    private fun detachAuthor(book: Book) {
        val ids = byAuthor[book.author] ?: return
        ids.remove(book.id)
        if (ids.isEmpty()) byAuthor.remove(book.author)
    }

    private fun authorMatches(query: String): List<String> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val exact = byAuthor[needle]
        if (exact != null) return exact.toList()
        return byAuthor.entries
            .filter { it.key.contains(needle, ignoreCase = true) }
            .flatMap { it.value }
    }

    private fun isDuplicate(title: String, author: String): Boolean =
        byId.values.any { it.title.equals(title, ignoreCase = true) && it.author.equals(author, ignoreCase = true) }

    private fun nextId(): String {
        val highest = byId.keys.mapNotNull { it.removePrefix(ID_PREFIX).toIntOrNull() }.maxOrNull() ?: 0
        return "$ID_PREFIX${highest + 1}"
    }

    private fun validateRating(rating: Int?) {
        if (rating != null && rating !in MIN_RATING..MAX_RATING) throw DomainError.InvalidRating(rating)
    }

    private fun normaliseTags(tags: Collection<String>): List<String> =
        tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    private fun normaliseId(id: String): String = id.trim().lowercase()

    companion object {
        const val MIN_RATING: Int = 1
        const val MAX_RATING: Int = 5

        private const val ID_PREFIX = "b"

        private val BOOK_ORDER: Comparator<Book> = compareBy<Book> { it.addedOn }.thenBy { it.id }
    }
}
