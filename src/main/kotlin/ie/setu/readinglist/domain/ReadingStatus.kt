package ie.setu.readinglist.domain

/**
 * The three states a book can be in. The CLI accepts the human-readable
 * labels (`to-read`, `reading`, `finished`) rather than enum names.
 */
enum class ReadingStatus(val label: String) {
    TO_READ("to-read"),
    READING("reading"),
    FINISHED("finished"),
    ;

    companion object {
        fun fromLabel(value: String): ReadingStatus? =
            entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }

        fun labels(): String = entries.joinToString(", ") { it.label }
    }
}
