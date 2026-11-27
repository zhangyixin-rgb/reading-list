package ie.setu.readinglist.cli

import ie.setu.readinglist.domain.Book
import ie.setu.readinglist.domain.DomainError
import ie.setu.readinglist.domain.LibraryStats
import ie.setu.readinglist.domain.ReadingList
import ie.setu.readinglist.domain.ReadingStatus
import ie.setu.readinglist.storage.ReadingListRepository
import ie.setu.readinglist.storage.StorageError
import java.time.LocalDate
import java.util.Locale

/**
 * Entry point for the command line application.
 *
 * [App] owns the flow — parse, load, execute, save — while the work itself is
 * delegated to [ReadingList] and the repository. Output goes to the supplied
 * [Appendable]s so tests can capture it without touching the real console.
 */
class App(
    private val repository: ReadingListRepository,
    private val out: Appendable,
    private val err: Appendable,
    private val clock: () -> LocalDate = LocalDate::now,
) {

    fun run(args: Array<String>): Int {
        val command = when (val parsed = ArgParser.parse(args)) {
            is ParseResult.Invalid -> return fail(parsed.message, ExitCode.USAGE)
            is ParseResult.Parsed -> parsed.command
        }
        if (command == Command.Help) {
            out.append(HELP_TEXT)
            return ExitCode.SUCCESS
        }
        return try {
            dispatch(command, ReadingList(repository.load(), clock))
        } catch (exception: DomainError.NotFound) {
            fail(exception.message ?: "Book not found", ExitCode.NOT_FOUND)
        } catch (exception: DomainError) {
            fail(exception.message ?: "Request rejected", ExitCode.VALIDATION)
        } catch (exception: StorageError) {
            fail(exception.message ?: "Storage failure", ExitCode.STORAGE)
        }
    }

    private fun dispatch(command: Command, list: ReadingList): Int = when (command) {
        is Command.Add -> {
            val book = list.add(command.title, command.author, command.status, command.rating, command.tags)
            persist(list)
            out.appendLine("Added ${book.id}: ${book.title} by ${book.author} (${book.status.label})")
            ExitCode.SUCCESS
        }

        is Command.ListBooks -> {
            out.append(BookTable.render(list.filter(command.status, command.author, command.sort)))
            ExitCode.SUCCESS
        }

        is Command.Show -> {
            out.append(describe(list.requireBook(command.id)))
            ExitCode.SUCCESS
        }

        is Command.Start -> {
            val book = list.start(command.id)
            persist(list)
            out.appendLine("Started ${book.id}: ${book.title}")
            ExitCode.SUCCESS
        }

        is Command.Finish -> {
            val book = list.finish(command.id, command.rating)
            persist(list)
            val rating = book.rating?.let { " with a rating of $it/${ReadingList.MAX_RATING}" } ?: ""
            out.appendLine("Finished ${book.id}: ${book.title}$rating")
            ExitCode.SUCCESS
        }

        is Command.Rate -> {
            val book = list.rate(command.id, command.rating)
            persist(list)
            out.appendLine("Rated ${book.id}: ${book.title} at ${book.rating}/${ReadingList.MAX_RATING}")
            ExitCode.SUCCESS
        }

        is Command.Remove -> {
            val book = list.remove(command.id)
            persist(list)
            out.appendLine("Removed ${book.id}: ${book.title}")
            ExitCode.SUCCESS
        }

        is Command.Next -> {
            val books = list.nextUp(command.limit)
            if (books.isEmpty()) {
                out.appendLine("Nothing left to read.")
            } else {
                out.append(BookTable.render(books))
            }
            ExitCode.SUCCESS
        }

        Command.Stats -> {
            out.append(renderStats(list.stats()))
            ExitCode.SUCCESS
        }

        Command.Help -> {
            out.append(HELP_TEXT)
            ExitCode.SUCCESS
        }
    }

    private fun persist(list: ReadingList) {
        repository.save(list.books())
    }

    private fun fail(message: String, code: Int): Int {
        err.appendLine("error: $message")
        if (code == ExitCode.USAGE) err.appendLine("Run 'reading-list help' for usage.")
        return code
    }

    private fun describe(book: Book): String = buildString {
        appendLine("id:       ${book.id}")
        appendLine("title:    ${book.title}")
        appendLine("author:   ${book.author}")
        appendLine("status:   ${book.status.label}")
        appendLine("rating:   ${book.rating?.let { "$it/${ReadingList.MAX_RATING}" } ?: "-"}")
        appendLine("added:    ${book.addedOn}")
        appendLine("finished: ${book.finishedOn ?: "-"}")
        appendLine("tags:     ${if (book.tags.isEmpty()) "-" else book.tags.joinToString(", ")}")
    }

    private fun renderStats(stats: LibraryStats): String = buildString {
        appendLine("Total books: ${stats.total}")
        ReadingStatus.entries.forEach { status ->
            appendLine("  ${status.label.padEnd(STATUS_LABEL_WIDTH)} ${stats.byStatus[status] ?: 0}")
        }
        val average = stats.averageRating
        val formatted = average?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "not rated yet"
        appendLine("Average rating: $formatted")
    }

    private companion object {
        const val STATUS_LABEL_WIDTH: Int = 9

        val HELP_TEXT: String = """
            reading-list - track what you are reading from the command line

            Usage:
              reading-list <command> [options]

            Commands:
              add --title <text> --author <text> [--status to-read|reading|finished]
                  [--rating 1-5] [--tag <text>]     Add a book to the list
              list [--status <status>] [--author <text>] [--sort added|title|author|rating]
                                                    List books, optionally filtered
              show <id>                             Show one book in detail
              start <id>                            Move a book from to-read to reading
              finish <id> [--rating 1-5]            Mark a book as finished
              rate <id> --rating 1-5                Change the rating of a finished book
              remove <id>                           Remove a book from the list
              next [--limit <n>]                    Show the next books to read
              stats                                 Show summary figures
              help                                  Show this message

            Exit codes:
              0 success   2 bad usage   3 not found   4 rejected   5 storage failure

            Environment:
              READING_LIST_DATA   Path of the JSON library file
                                  (default: ~/.reading-list/library.json)
        """.trimIndent() + "\n"
    }
}
