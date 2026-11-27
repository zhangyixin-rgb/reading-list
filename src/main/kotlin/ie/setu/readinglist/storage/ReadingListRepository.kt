package ie.setu.readinglist.storage

import ie.setu.readinglist.domain.Book

/** Where the reading list is read from and written to. */
interface ReadingListRepository {
    /** @return the stored books, or an empty list when nothing has been stored yet. */
    fun load(): List<Book>

    /** Replaces the stored contents with [books]. */
    fun save(books: List<Book>)
}

/** Raised when the backing store cannot be read or written. */
class StorageError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
