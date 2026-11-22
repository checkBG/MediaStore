package org.example.project.cloud.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Тело POST /api/v2/file/remove.
 *
 * Реальный запрос:
 * {"x-email":"<EMAIL>","home":"/path/to/file"}
 */
@Serializable
data class RemoveFileRequest(
    @SerialName("x-email") val xEmail: String,
    val home: String
)

/**
 * Ответ POST /api/v2/file/remove.
 *
 * status — логический статус (200 = успех).
 * time   — epoch в миллисекундах.
 */
@Serializable
data class RemoveResponse(
    val email: String? = null,
    val body: String? = null,
    val status: Int = 0,
    val time: Long? = null
)
