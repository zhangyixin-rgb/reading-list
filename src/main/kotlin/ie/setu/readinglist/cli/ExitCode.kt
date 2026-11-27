package ie.setu.readinglist.cli

/** Process exit codes. They are part of the CLI contract, so they are named. */
object ExitCode {
    const val SUCCESS: Int = 0
    const val USAGE: Int = 2
    const val NOT_FOUND: Int = 3
    const val VALIDATION: Int = 4
    const val STORAGE: Int = 5
}
