package org.example.project.model

data class Photo(
    val id: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String, // jpeg, png и тд
    val dateAdded: Long, // дата добавления в секундах
    val size: Long, // размер файла в байтах
    val width: Int, // ширина в пикселях
    val height: Int, // высота в пикселях
    val bucketId: String?, // ID альбома (папки), в которой лежит фото
    val bucketDisplayName: String? // удобно читаемое имя альбома
)

/**
    Класс Альбома. В MediaStore - это просто группа фото с одинаковыми bucketId
 */
data class Album(
    val id: String,
    val name: String,
    val coverPhotoUri: String?,
    val photoCount: Int
)