package de.ljunker.queuedos.application

enum class FailureKind {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT
}

open class QueueDosFailure(
    val kind: FailureKind,
    override val message: String
) : RuntimeException(message)

class BadRequestFailure(message: String) : QueueDosFailure(FailureKind.BAD_REQUEST, message)

class UnauthorizedFailure(message: String) : QueueDosFailure(FailureKind.UNAUTHORIZED, message)

class ForbiddenFailure(message: String) : QueueDosFailure(FailureKind.FORBIDDEN, message)

class NotFoundFailure(message: String) : QueueDosFailure(FailureKind.NOT_FOUND, message)

class ConflictFailure(message: String) : QueueDosFailure(FailureKind.CONFLICT, message)
