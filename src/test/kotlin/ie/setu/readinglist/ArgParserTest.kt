package ie.setu.readinglist

import ie.setu.readinglist.cli.ArgParser
import ie.setu.readinglist.cli.Command
import ie.setu.readinglist.cli.ParseResult
import ie.setu.readinglist.domain.ReadingStatus
import ie.setu.readinglist.domain.SortOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArgParserTest {

    private fun command(vararg args: String): Command {
        val result = ArgParser.parse(arrayOf(*args))
        assertTrue(result is ParseResult.Parsed, "expected a parsed command but got $result")
        return (result as ParseResult.Parsed).command
    }

    private fun error(vararg args: String): String {
        val result = ArgParser.parse(arrayOf(*args))
        assertTrue(result is ParseResult.Invalid, "expected a usage error but got $result")
        return (result as ParseResult.Invalid).message
    }

    @Test
    fun `no arguments shows help`() {
        assertEquals(Command.Help, command())
    }

    @Test
    fun `help can be requested explicitly`() {
        assertEquals(Command.Help, command("help"))
        assertEquals(Command.Help, command("--help"))
    }

    @Test
    fun `add parses every option`() {
        val parsed = command(
            "add",
            "--title", "Dune",
            "--author", "Frank Herbert",
            "--status", "finished",
            "--rating", "5",
            "--tag", "sci-fi",
            "--tag", "classic",
        )

        assertEquals(
            Command.Add("Dune", "Frank Herbert", ReadingStatus.FINISHED, 5, listOf("sci-fi", "classic")),
            parsed,
        )
    }

    @Test
    fun `add defaults to the to-read status`() {
        val parsed = command("add", "--title", "Dune", "--author", "Frank Herbert")

        assertEquals(Command.Add("Dune", "Frank Herbert", ReadingStatus.TO_READ, null, emptyList()), parsed)
    }

    @Test
    fun `add reports a missing title`() {
        val message = error("add", "--author", "Frank Herbert")

        assertTrue(message.contains("--title"), message)
    }

    @Test
    fun `add rejects an unknown status`() {
        val message = error("add", "--title", "Dune", "--author", "Frank Herbert", "--status", "lost")

        assertTrue(message.contains("--status"), message)
    }

    @Test
    fun `add rejects a non numeric rating`() {
        val message = error("add", "--title", "Dune", "--author", "Frank Herbert", "--rating", "five")

        assertTrue(message.contains("--rating"), message)
    }

    @Test
    fun `list defaults to the added ordering`() {
        assertEquals(Command.ListBooks(null, null, SortOrder.ADDED), command("list"))
    }

    @Test
    fun `list parses filters`() {
        val parsed = command("list", "--status", "reading", "--author", "austen", "--sort", "title")

        assertEquals(Command.ListBooks(ReadingStatus.READING, "austen", SortOrder.TITLE), parsed)
    }

    @Test
    fun `show needs exactly one id`() {
        assertEquals(Command.Show("b1"), command("show", "b1"))
        assertTrue(error("show").contains("book id"))
        assertTrue(error("show", "b1", "b2").contains("book id"))
    }

    @Test
    fun `finish accepts an optional rating`() {
        assertEquals(Command.Finish("b1", null), command("finish", "b1"))
        assertEquals(Command.Finish("b1", 4), command("finish", "b1", "--rating", "4"))
    }

    @Test
    fun `rate accepts the score as an option or as a second positional argument`() {
        assertEquals(Command.Rate("b1", 3), command("rate", "b1", "--rating", "3"))
        assertEquals(Command.Rate("b1", 3), command("rate", "b1", "3"))
    }

    @Test
    fun `rate without a score is rejected`() {
        assertTrue(error("rate", "b1").contains("--rating"))
    }

    @Test
    fun `next defaults to five books`() {
        assertEquals(Command.Next(5), command("next"))
        assertEquals(Command.Next(2), command("next", "--limit", "2"))
    }

    @Test
    fun `next rejects a non positive limit`() {
        assertTrue(error("next", "--limit", "0").contains("--limit"))
    }

    @Test
    fun `stats takes no options`() {
        assertEquals(Command.Stats, command("stats"))
        assertTrue(error("stats", "--limit", "3").contains("Unknown option"))
    }

    @Test
    fun `unknown commands are reported`() {
        val message = error("borrow", "b1")

        assertTrue(message.contains("borrow"), message)
        assertTrue(message.contains("help"), message)
    }

    @Test
    fun `unknown options are rejected instead of ignored`() {
        val message = error("list", "--status", "reading", "--colour", "blue")

        assertTrue(message.contains("--colour"), message)
    }

    @Test
    fun `an option without a value is reported`() {
        val message = error("add", "--title", "Dune", "--author")

        assertTrue(message.contains("--author"), message)
    }
}
