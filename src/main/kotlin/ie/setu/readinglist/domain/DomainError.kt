package ie.setu.readinglist.domain

/**
 * Errors raised when a request is well-formed but cannot be applied to the
 * current state of the reading list. The CLI turns these into non-zero exit
 * codes instead of stack traces.
 */
sealed class DomainError(message: String) : RuntimeException(message) {

    class NotFound(val id: String) : DomainError("No book with id '$id'")

    class Duplicate(val title: String, val author: String) :
        DomainError("'$title' by $author is already in the list")

    class BlankTitle : DomainError("Title must not be blank")

    class BlankAuthor : DomainError("Author must not be blank")

    class InvalidRating(val value: Int) :
        DomainError("Rating must be between ${ReadingList.MIN_RATING} and ${ReadingList.MAX_RATING}, got $value")

    class RatingNeedsFinished :
        DomainError("A rating can only be recorded for a finished book")

    class InvalidTransition(val id: String, val from: ReadingStatus, val to: ReadingStatus) :
        DomainError("Book '$id' is '$from' and cannot move to '$to'")
}
