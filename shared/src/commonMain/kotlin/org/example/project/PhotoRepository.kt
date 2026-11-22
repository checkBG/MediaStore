package org.example.project

import org.example.project.model.Album
import org.example.project.model.Photo

/**
    Контракт репозитория. Общий код будет работать
    только с этим интерфейсом и не знать, что под капотом Android MediaStore
    Так как сначала мы пишем для Android, а в будущем, возможно, расширимся до IOS
 */
interface PhotoRepository {

    /**
        Получить список фотографий.
        @param albumId - если задан, то вернёт только фото этого альбома (bucketId)
        @param fromDate - фильтр "не старше"
        @param toDate - фильтр "не новее"
        @param limit - сколько максимум вернуть
        @param offset - сколько пропустить
     */
    suspend fun getPhotos(
        albumId: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<Photo>

    /**
     * Получить список альбомов
     */
    suspend fun getAlbums(): List<Album>

    /**
     * Получить миниатюру фото в виде JPEG-байтов (для отображения в списке)
     */
    suspend fun getThumbnail(photoId: Long): ByteArray?

    /**
     * Удалить фото. Возращает true при успехе
     */
    suspend fun deletePhoto(photo: Photo): Boolean
}