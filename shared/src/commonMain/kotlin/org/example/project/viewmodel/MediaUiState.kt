package org.example.project.viewmodel

import org.example.project.model.Album
import org.example.project.model.Photo

/**
 * Состояние экрана медиатеки.
 *
 * photos/albums - локальная медиатека.
 * cloudPath/cloudDelete - сценарий удаления в облаке.
 */
data class MediaUiState(
    val photos: List<Photo> = emptyList(),
    val albums: List<Album> = emptyList(),
    val selectedAlbumId: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val thumbnails: Map<Long, ByteArray> = emptyMap(),
    val errorMessage: String? = null,
    val message: String? = null,
    val cloudPath: String = "",
    val cloudDelete: CloudDeleteState = CloudDeleteState.Idle
)

sealed interface CloudDeleteState {
    data object Idle : CloudDeleteState
    data object Loading : CloudDeleteState
    data class Success(val path: String) : CloudDeleteState
    data class Error(val message: String) : CloudDeleteState
}
