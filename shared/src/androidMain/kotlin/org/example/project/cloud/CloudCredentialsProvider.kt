package org.example.project.cloud

/** интерфейс-поставщик учётных данных для сетевого слоя */
interface CloudCredentialsProvider {
    fun email(): String
    fun csrfToken(): String
    fun pageId(): String?
}
