package ie.setu.readinglist.cli

import ie.setu.readinglist.domain.Book
import ie.setu.readinglist.domain.ReadingList

/**
 * Renders books as a fixed-width table. Widths are capped so that a long title
 * does not push the author column off the screen.
 */
internal object BookTable {
    private const val ID_WIDTH = 5
    private const val STATUS_WIDTH = 10
    private const val RATING_WIDTH = 8
    private const val TITLE_WIDTH = 34
    private const val AUTHOR_WIDTH = 24

    fun render(books: List<Book>): String {
        if (books.isEmpty()) return "No books to show.\n"
        val header = row("ID", "STATUS", "RATING", "TITLE", "AUTHOR")
        val rows = books.map { row(it.id, it.status.label, rating(it), it.title, it.author) }
        return buildString {
            append(header)
            rows.forEach(::append)
        }
    }

    private fun rating(book: Book): String =
        book.rating?.let { "$it/${ReadingList.MAX_RATING}" } ?: "-"

    private fun row(id: String, status: String, rating: String, title: String, author: String): String =
        cell(id, ID_WIDTH) +
            cell(status, STATUS_WIDTH) +
            cell(rating, RATING_WIDTH) +
            cell(title, TITLE_WIDTH) +
            cell(author, AUTHOR_WIDTH) +
            "\n"

    private fun cell(value: String, width: Int): String {
        val clipped = if (value.length > width) value.take(width - 1) + "…" else value
        return clipped.padEnd(width)
    }
}
