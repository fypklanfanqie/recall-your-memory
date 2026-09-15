package com.echo.recall.core.data

import com.echo.recall.core.data.db.TodoDao
import com.echo.recall.core.data.db.TodoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val dao: TodoDao,
) {

    fun observeAll(): Flow<List<TodoEntity>> = dao.observeAll()

    fun observeById(id: String): Flow<TodoEntity?> = dao.observeById(id)

    suspend fun findById(id: String): TodoEntity? = dao.findById(id)

    /** 新建待办，返回新 id。 */
    suspend fun create(title: String, notes: String? = null, dueAt: Long? = null): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            dao.upsert(
                TodoEntity(
                    id = id,
                    title = title.trim(),
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = System.currentTimeMillis(),
                    dueAt = dueAt,
                ),
            )
            id
        }

    suspend fun upsert(todo: TodoEntity) = withContext(Dispatchers.IO) { dao.upsert(todo) }

    /**
     * 批量新建（例如「从记忆提取待办」），返回实际创建条数。
     * 空标题会被跳过；[notes] 用于标注来源。
     */
    suspend fun createMany(titles: List<String>, notes: String? = null): Int =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            var created = 0
            titles.forEach { title ->
                val clean = title.trim()
                if (clean.isEmpty()) return@forEach
                dao.upsert(
                    TodoEntity(
                        id = UUID.randomUUID().toString(),
                        title = clean,
                        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                        createdAt = now,
                    ),
                )
                created++
            }
            created
        }

    suspend fun setDone(id: String, done: Boolean) = withContext(Dispatchers.IO) {
        dao.setDone(id, done, if (done) System.currentTimeMillis() else null)
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        dao.setPinned(id, pinned)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun count(): Int = dao.count()
}
