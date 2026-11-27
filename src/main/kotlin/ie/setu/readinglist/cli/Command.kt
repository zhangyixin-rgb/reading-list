package ie.setu.readinglist.cli

import ie.setu.readinglist.domain.ReadingStatus
import ie.setu.readinglist.domain.SortOrder

/** A parsed command, ready to be executed by [App]. */
sealed interface Command {
    data class Add(
        val title: String,
        val author: String,
        val status: ReadingStatus,
        val rating: Int?,
        val tags: List<String>,
    ) : Command

    data class ListBooks(
        val status: ReadingStatus?,
        val author: String?,
        val sort: SortOrder,
    ) : Command

    data class Show(val id: String) : Command

    data class Start(val id: String) : Command

    data class Finish(val id: String, val rating: Int?) : Command

    data class Rate(val id: String, val rating: Int) : Command

    data class Remove(val id: String) : Command

    data class Next(val limit: Int) : Command

    data object Stats : Command

    data object Help : Command
}
