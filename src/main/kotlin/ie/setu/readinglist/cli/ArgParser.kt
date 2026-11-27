package ie.setu.readinglist.cli

import ie.setu.readinglist.domain.ReadingStatus
import ie.setu.readinglist.domain.SortOrder

/** Outcome of parsing a command line. */
sealed interface ParseResult {
    data class Parsed(val command: Command) : ParseResult

    data class Invalid(val message: String) : ParseResult
}

/** Raised internally when the command line cannot be understood. */
private class UsageException(message: String) : RuntimeException(message)

/**
 * Turns raw command-line arguments into a [Command].
 *
 * Parsing is deliberately strict: unknown options are rejected rather than
 * ignored, because a typo in `--ratng` should not silently drop the rating.
 */
object ArgParser {

    private const val DEFAULT_NEXT_LIMIT = 5

    fun parse(args: Array<String>): ParseResult =
        try {
            ParseResult.Parsed(parseCommand(args.toList()))
        } catch (exception: UsageException) {
            ParseResult.Invalid(exception.message ?: "Invalid arguments")
        }

    private fun parseCommand(args: List<String>): Command {
        val name = args.firstOrNull()?.lowercase() ?: return Command.Help
        if (name == "--help" || name == "-h" || name == "help") return Command.Help

        val arguments = Arguments(args.drop(1))
        return when (name) {
            "add" -> parseAdd(arguments)
            "list" -> parseList(arguments)
            "show" -> parseSimple(arguments, "show") { Command.Show(it) }
            "start" -> parseSimple(arguments, "start") { Command.Start(it) }
            "remove" -> parseSimple(arguments, "remove") { Command.Remove(it) }
            "finish" -> parseFinish(arguments)
            "rate" -> parseRate(arguments)
            "next" -> parseNext(arguments)
            "stats" -> {
                arguments.rejectUnknown(emptySet(), "stats")
                Command.Stats
            }
            else -> throw UsageException("Unknown command '$name'. Run 'reading-list help' for the list of commands.")
        }
    }

    private fun parseAdd(arguments: Arguments): Command {
        arguments.rejectUnknown(setOf("title", "author", "status", "rating", "tag"), "add")
        val statusLabel = arguments.value("status")
        val status = if (statusLabel == null) {
            ReadingStatus.TO_READ
        } else {
            ReadingStatus.fromLabel(statusLabel)
                ?: throw UsageException("Unknown --status '$statusLabel'. Use one of: ${ReadingStatus.labels()}")
        }
        return Command.Add(
            title = arguments.required("title", "add"),
            author = arguments.required("author", "add"),
            status = status,
            rating = arguments.optionalInt("rating"),
            tags = arguments.values("tag"),
        )
    }

    private fun parseList(arguments: Arguments): Command {
        arguments.rejectUnknown(setOf("status", "author", "sort"), "list")
        val statusLabel = arguments.value("status")
        val status = statusLabel?.let {
            ReadingStatus.fromLabel(it)
                ?: throw UsageException("Unknown --status '$it'. Use one of: ${ReadingStatus.labels()}")
        }
        val sortLabel = arguments.value("sort")
        val sort = sortLabel?.let {
            SortOrder.fromLabel(it)
                ?: throw UsageException("Unknown --sort '$it'. Use one of: ${SortOrder.labels()}")
        } ?: SortOrder.ADDED
        return Command.ListBooks(status = status, author = arguments.value("author"), sort = sort)
    }

    private fun parseSimple(arguments: Arguments, name: String, build: (String) -> Command): Command {
        arguments.rejectUnknown(emptySet(), name)
        return build(arguments.singleId(name))
    }

    private fun parseFinish(arguments: Arguments): Command {
        arguments.rejectUnknown(setOf("rating"), "finish")
        return Command.Finish(id = arguments.singleId("finish"), rating = arguments.optionalInt("rating"))
    }

    private fun parseRate(arguments: Arguments): Command {
        arguments.rejectUnknown(setOf("rating"), "rate")
        val ids = arguments.positional
        if (ids.isEmpty() || ids.size > 2) {
            throw UsageException("The 'rate' command needs a book id and a score, for example: rate b1 --rating 4")
        }
        val inlineRating = ids.getOrNull(1)?.toIntOrNull()
        val rating = arguments.optionalInt("rating") ?: inlineRating
            ?: throw UsageException("The 'rate' command needs --rating between 1 and 5")
        return Command.Rate(id = ids[0], rating = rating)
    }

    private fun parseNext(arguments: Arguments): Command {
        arguments.rejectUnknown(setOf("limit"), "next")
        if (arguments.positional.isNotEmpty()) throw UsageException("The 'next' command takes no positional arguments")
        val limit = arguments.optionalInt("limit") ?: DEFAULT_NEXT_LIMIT
        if (limit < 1) throw UsageException("--limit must be 1 or greater")
        return Command.Next(limit)
    }
}

/** Named options and positional arguments extracted from the tokens after the command. */
private class Arguments(raw: List<String>) {

    private val named = LinkedHashMap<String, MutableList<String>>()
    val positional = mutableListOf<String>()

    init {
        var index = 0
        while (index < raw.size) {
            val token = raw[index]
            if (token.startsWith("--")) {
                val name = token.removePrefix("--")
                val value = raw.getOrNull(index + 1)
                if (name.isEmpty() || value == null || value.startsWith("--")) {
                    throw UsageException("Option '$token' needs a value")
                }
                named.getOrPut(name) { mutableListOf() }.add(value)
                index += 2
            } else {
                positional.add(token)
                index += 1
            }
        }
    }

    fun value(name: String): String? = named[name]?.lastOrNull()

    fun values(name: String): List<String> = named[name] ?: emptyList()

    fun required(name: String, command: String): String =
        value(name) ?: throw UsageException("The '$command' command needs --$name")

    fun optionalInt(name: String): Int? {
        val rawValue = value(name) ?: return null
        return rawValue.toIntOrNull() ?: throw UsageException("--$name must be a whole number, got '$rawValue'")
    }

    fun singleId(command: String): String {
        if (positional.size != 1) {
            throw UsageException("The '$command' command needs exactly one book id, for example: $command b1")
        }
        return positional[0]
    }

    fun rejectUnknown(allowed: Set<String>, command: String) {
        val unknown = named.keys.firstOrNull { it !in allowed } ?: return
        throw UsageException("Unknown option --$unknown for '$command'")
    }
}
