package ie.setu.readinglist.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * A single entry in the reading list.
 *
 * Invariants (enforced by [ReadingList], not by the constructor):
 *  - [title] and [author] are non-blank and trimmed;
 *  - [rating] is null or in 1..5 and only present when [status] is [ReadingStatus.FINISHED];
 *  - [finishedOn] is set if and only if the book is finished.
 */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val status: ReadingStatus = ReadingStatus.TO_READ,
    val rating: Int? = null,
    @Serializable(with = IsoDateSerializer::class)
    val addedOn: LocalDate,
    @Serializable(with = IsoDateSerializer::class)
    val finishedOn: LocalDate? = null,
    val tags: List<String> = emptyList(),
)
