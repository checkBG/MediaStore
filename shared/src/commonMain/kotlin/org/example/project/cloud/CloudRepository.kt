package org.example.project.cloud


/** Минимальный общий контракт облака:
 *      пока единственная операция - удаление файла по полному пути. */
interface CloudRepository {

    /**
     * @param path полный путь в облаке
     */
    suspend fun removeFile(path: String): CloudResult<Unit>
}
