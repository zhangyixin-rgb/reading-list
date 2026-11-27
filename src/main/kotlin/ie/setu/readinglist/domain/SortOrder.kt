package ie.setu.readinglist.domain

/** How a listing should be ordered. */
enum class SortOrder {
    ADDED,
    TITLE,
    AUTHOR,
    RATING,
    ;

    val comparator: Comparator<Book>
        get() = when (this) {
            ADDED -> compareBy<Book> { it.addedOn }.thenBy { it.id }
            TITLE -> compareBy<Book> { it.title.lowercase() }.thenBy { it.author.lowercase() }
            AUTHOR -> compareBy<Book> { it.author.lowercase() }.thenBy { it.title.lowercase() }
            RATING -> compareByDescending<Book> { it.rating ?: 0 }.thenBy { it.title.lowercase() }
        }

    companion object {
        fun fromLabel(value: String): SortOrder? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

        fun labels(): String = entries.joinToString(", ") { it.name.lowercase() }
    }
}
