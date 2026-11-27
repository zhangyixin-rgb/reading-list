package ie.setu.readinglist

import ie.setu.readinglist.cli.App
import ie.setu.readinglist.storage.JsonFileRepository
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Wires the real process streams and the default data file to [App].
 * The data file can be overridden with the READING_LIST_DATA environment
 * variable, which keeps the application testable and lets a demo run against a
 * throw-away library.
 */
fun main(args: Array<String>) {
    val app = App(
        repository = JsonFileRepository(dataFile()),
        out = System.out,
        err = System.err,
    )
    exitProcess(app.run(args))
}

private fun dataFile(): Path {
    val override = System.getenv("READING_LIST_DATA")
    if (!override.isNullOrBlank()) return Path.of(override)
    return Path.of(System.getProperty("user.home"), ".reading-list", "library.json")
}
