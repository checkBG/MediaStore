package org.example.project.cloud.network

import org.example.project.cloud.model.RemoveFileRequest
import org.example.project.cloud.model.RemoveResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface CloudMailApi {

    /**
     * Удалить файл в облаке.
     *
     * Тело — JSON:
     * {"x-email":"<EMAIL>","home":"/path/to/file"}
     *
     * X-CSRF-Token, X-Email, X-Page-Id добавляет AuthInterceptor.
     */
    @POST("api/v2/file/remove")
    suspend fun removeFile(
        @Body body: RemoveFileRequest,
        @Header("X-API-Version") apiVersion: String = "2"
    ): RemoveResponse
}
