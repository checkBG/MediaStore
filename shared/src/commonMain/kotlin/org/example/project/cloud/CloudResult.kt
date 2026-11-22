package org.example.project.cloud

sealed interface CloudResult<out T> {

    data class Success<T>(val value: T) : CloudResult<T>

    /** HTTP 4xx/5xx, кроме 401/403. */
    data class ApiError(
        val code: Int,
        val message: String?
    ) : CloudResult<Nothing>

    /** 401/403: cookies или CSRF token истекли. */
    data class Unauthorized(
        val message: String?
    ) : CloudResult<Nothing>

    /** Нет сети, таймаут, DNS, TLS. */
    data class NetworkError(
        val cause: Throwable
    ) : CloudResult<Nothing>

    /** Ошибка до запроса: например, путь не начинается с "/". */
    data class InvalidInput(
        val message: String
    ) : CloudResult<Nothing>

    /** Неожиданная ошибка: парсинг ответа, заголовки, неизвестный сбой. */
    data class UnknownError(
        val message: String?
    ) : CloudResult<Nothing>
}
