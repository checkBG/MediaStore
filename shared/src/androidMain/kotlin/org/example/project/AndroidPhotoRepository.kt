package org.example.project

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.core.net.toUri
import org.example.project.model.Album
import org.example.project.model.Photo

/**
 * Android-реализация репозитория. Использует ContentResolver + MediaStore.
 * Context нужен, чтобы получить ContentReosolver - он даёт доступ к базе медиа
 */
class AndroidPhotoRepository(
    private val context: Context
) : PhotoRepository {
    /**
     * contentResolver - "точка входа" для всех запросов к MediaStore
     * Берём его один раз из Context, чтобы не дёргать каждый раз
     */
    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun getPhotos(
        albumId: String?,
        fromDate: Long?,
        toDate: Long?,
        limit: Int,
        offset: Int
    ): List<Photo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        albumId?.let {
            selectionParts.add("${MediaStore.Images.Media.BUCKET_ID} = ?")
            selectionArgs.add(it)
        }
        fromDate?.let {
            selectionParts.add("${MediaStore.Images.Media.DATE_ADDED} >= ?")
            selectionArgs.add(it.toString())
        }
        toDate?.let {
            selectionParts.add("${MediaStore.Images.Media.DATE_ADDED} <= ?")
            selectionArgs.add(it.toString())
        }

        val selection = selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val selectionArgsArray = selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray()

        val photos = mutableListOf<Photo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // --- Android 11+ (API 30+): Используем Bundle для лимитов ---
            val queryArgs = Bundle().apply {
                if (!selection.isNullOrEmpty()) {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                }

                if (!selectionArgsArray.isNullOrEmpty()) {
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgsArray)
                }

                // Сортировка: новые сверху
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_ADDED)
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                // Пагинация
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                queryArgs, // queryArgs <--- Передаем Bundle сюда
                null
            )?.use { cursor ->
                parsePhotos(cursor, photos)
            }
        } else {
            // --- Android 10 и ниже: Старый способ через sortOrder ---
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit OFFSET $offset"

            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgsArray,
                sortOrder
            )?.use { cursor ->
                parsePhotos(cursor, photos)
            }
        }

        photos
    }

    // Вынесем парсинг курсора в отдельную функцию, чтобы не дублировать код
    private fun parsePhotos(cursor: Cursor, photos: MutableList<Photo>) {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val bucketNameCol =
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
            ).toString()

            photos.add(
                Photo(
                    id = id,
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: "",
                    mimeType = cursor.getString(mimeCol) ?: "image/*",
                    dateAdded = cursor.getLong(dateCol),
                    size = cursor.getLong(sizeCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    bucketId = cursor.getString(bucketIdCol),
                    bucketDisplayName = cursor.getString(bucketNameCol)
                )
            )
        }
    }

    override suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        // Новые фото идут первыми, поэтому первое фото альбома станет обложкой.
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val albums = linkedMapOf<String, Album>()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketIdCol) ?: continue
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val coverId = cursor.getLong(idCol)
                val coverUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, coverId
                ).toString()

                val existing = albums[bucketId]
                albums[bucketId] = if (existing == null) {
                    Album(
                        id = bucketId,
                        name = bucketName,
                        coverPhotoUri = coverUri,
                        photoCount = 1
                    )
                } else {
                    existing.copy(photoCount = existing.photoCount + 1)
                }
            }
        }

        albums.values.toList()
    }

    /* Загрузка миниатюры */
    override suspend fun getThumbnail(photoId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId
        )

        try {
            // Современный способ (API 29+)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(256, 256), null)
            } else {
                // Устаревший способ для старых API
                @Suppress("DEPRECATION")
                MediaStore.Images.Thumbnails.getThumbnail(
                    contentResolver, photoId,
                    MediaStore.Images.Thumbnails.MINI_KIND, null
                )
            }

            bitmap?.let {
                val output = ByteArrayOutputStream()
                it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
                it.recycle() // переиспользование памяти
                output.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = photo.uri.toUri()
            contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}