package org.example.project.cloud

import kotlinx.coroutines.CancellationException
import org.example.project.cloud.model.RemoveFileRequest
import org.example.project.cloud.network.CloudAuthConfigException
import org.example.project.cloud.network.CloudMailApi
import retrofit2.HttpException
import java.io.IOException

class CloudMailRepository(
    private val api: CloudMailApi,
    private val credentials: CloudCredentialsProvider
) : CloudRepository {

    override suspend fun removeFile(path: String): CloudResult<Unit> {
        if (!path.startsWith("/")) {
            return CloudResult.InvalidInput(
                "Путь в облаке должен начинаться с \"/\": $path"
            )
        }

        return try {
            val response = api.removeFile(
                body = RemoveFileRequest(
                    xEmail = credentials.email(),
                    home = path
                )
            )

            if (response.status == 200) {
                CloudResult.Success(Unit)
            } else {
                CloudResult.ApiError(response.status, response.body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            mapHttpError(e)
        } catch (e: CloudAuthConfigException) {
            CloudResult.InvalidInput(
                e.message ?: "Некорректные заголовки авторизации"
            )
        } catch (e: IOException) {
            CloudResult.NetworkError(e)
        } catch (e: Exception) {
            CloudResult.UnknownError(e.message)
        }
    }

    private fun mapHttpError(e: HttpException): CloudResult<Nothing> {
        val code = e.code()
        val message = runCatching {
            e.response()?.errorBody()?.string()
        }.getOrNull()

        return if (code == 401 || code == 403) {
            CloudResult.Unauthorized(message)
        } else {
            CloudResult.ApiError(code, message)
        }
    }
}
