package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.PhotoRepository
import org.example.project.cloud.CloudRepository
import org.example.project.cloud.CloudResult
import org.example.project.model.Photo

/**
 * Общий ViewModel для локальной медиатеки и облачного удаления.
 *
 * Локальная часть:
 * - загрузка фото и альбомов;
 * - миниатюры;
 * - удаление локального файла.
 *
 * Облачная часть:
 * - ввод пути;
 * - removeFile в Cloud Mail.ru.
 */
class MediaViewModel(
    private val photoRepository: PhotoRepository,
    private val cloudRepository: CloudRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    // UI вызывает refresh() после получения разрешения на доступ к медиа.

    /**
     * Перезагрузить альбомы и фотографии текущего альбома.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            try {
                val albums = photoRepository.getAlbums()
                val photos = photoRepository.getPhotos(
                    albumId = _uiState.value.selectedAlbumId,
                    limit = PHOTO_LIMIT
                )

                _uiState.update {
                    it.copy(
                        albums = albums,
                        photos = photos,
                        isRefreshing = false,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = e.message ?: "Не удалось загрузить медиатеку"
                    )
                }
            }
        }
    }

    /**
     * Открыть альбом или вернуться ко всем фото (albumId = null).
     */
    fun selectAlbum(albumId: String?) {
        _uiState.update {
            it.copy(
                selectedAlbumId = albumId,
                photos = emptyList()
            )
        }
        loadPhotos(albumId)
    }

    fun loadPhotos(albumId: String? = _uiState.value.selectedAlbumId) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val photos = photoRepository.getPhotos(
                    albumId = albumId,
                    limit = PHOTO_LIMIT
                )
                _uiState.update { it.copy(photos = photos, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Не удалось загрузить фотографии"
                    )
                }
            }
        }
    }

    /*TODO()*/
    fun loadAlbums() {
        viewModelScope.launch {
            try {
                val albums = photoRepository.getAlbums()
                _uiState.update { it.copy(albums = albums) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Не удалось загрузить альбомы")
                }
            }
        }
    }

    /**
     * Тестовая загрузка миниатюры через MediaStore
     * TODO()
     */
    fun loadThumbnail(photoId: Long) {
        viewModelScope.launch {
            val bytes = try {
                photoRepository.getThumbnail(photoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (bytes != null) {
                _uiState.update {
                    it.copy(thumbnails = it.thumbnails + (photoId to bytes))
                }
            } else {
                _uiState.update {
                    it.copy(message = "Не удалось загрузить миниатюру")
                }
            }
        }
    }

    /**
     * Прямое удаление локального файла через ContentResolver.
     *
     * На Android 11+ для чужих файлов нужен системный диалог:
     * UI вызывает MediaStore.createDeleteRequest, а после успеха —
     * onLocalPhotoRemoved(photo).
     */
    fun deleteLocalPhoto(photo: Photo) {
        viewModelScope.launch {
            val deleted = try {
                photoRepository.deletePhoto(photo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }

            if (deleted) {
                onLocalPhotoRemoved(photo)
            } else {
                _uiState.update {
                    it.copy(message = "Не удалось удалить локальный файл")
                }
            }
        }
    }

    /**
     * Вызывать после успешного системного подтверждения удаления.
     */
    fun onLocalPhotoRemoved(photo: Photo) {
        _uiState.update {
            it.copy(
                photos = it.photos.filterNot { item -> item.id == photo.id },
                message = "Локальный файл удалён: ${photo.displayName}"
            )
        }
    }

    fun onLocalDeleteCancelled() {
        _uiState.update { it.copy(message = "Удаление отменено") }
    }

    /** сообщение об ошибке системного диалога */
    fun onLocalDeleteFailed(message: String) {
        _uiState.update {
            it.copy(message = "Не удалось удалить: $message")
        }
    }

    /** обновляет введённый путь в состоянии */
    fun onCloudPathChange(path: String) {
        _uiState.update { it.copy(cloudPath = path) }
    }

    /**
     * валидирует непустой путь, вызывает cloudRepository.removeFile, маппит все CloudResult в
     * CloudDeleteState и message.
     *
     * path по умолчанию берётся из поля cloudPath.
     */
    fun removeCloudFile(path: String = _uiState.value.cloudPath) {
        val cloudPath = path.trim()

        if (cloudPath.isBlank()) {
            _uiState.update {
                it.copy(cloudDelete = CloudDeleteState.Error("Укажи путь в облаке"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(cloudDelete = CloudDeleteState.Loading) }

            val result = try {
                cloudRepository.removeFile(cloudPath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CloudResult.UnknownError(e.message)
            }

            when (result) {
                is CloudResult.Success -> {
                    _uiState.update {
                        it.copy(
                            cloudDelete = CloudDeleteState.Success(cloudPath),
                            message = "Удалено в облаке: $cloudPath"
                        )
                    }
                }

                is CloudResult.ApiError -> {
                    _uiState.update {
                        it.copy(
                            cloudDelete = CloudDeleteState.Error(
                                "Ошибка API ${result.code}: ${result.message.orEmpty()}"
                            )
                        )
                    }
                }

                is CloudResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            cloudDelete = CloudDeleteState.Error(
                                "Сессия истекла. Обнови cookies/token."
                            )
                        )
                    }
                }

                is CloudResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            cloudDelete = CloudDeleteState.Error(
                                "Нет связи: ${result.cause.message.orEmpty()}"
                            )
                        )
                    }
                }

                is CloudResult.InvalidInput -> {
                    _uiState.update {
                        it.copy(cloudDelete = CloudDeleteState.Error(result.message))
                    }
                }

                is CloudResult.UnknownError -> {
                    _uiState.update {
                        it.copy(
                            cloudDelete = CloudDeleteState.Error(
                                "Неизвестная ошибка: ${result.message.orEmpty()}"
                            )
                        )
                    }
                }
            }
        }
    }

    /** сбрасывает статус удаления в Idle */
    fun clearCloudDeleteState() {
        _uiState.update { it.copy(cloudDelete = CloudDeleteState.Idle) }
    }

    /** очищает snackbar-сообщение */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** TODO() */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        const val PHOTO_LIMIT = 200
    }
}
