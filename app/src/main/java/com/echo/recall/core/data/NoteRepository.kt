package com.echo.recall.core.data

import com.echo.recall.core.data.db.NoteDao
import com.echo.recall.core.data.db.NoteEntity
import com.echo.recall.core.util.NoteTextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val dao: NoteDao,
) {

    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    fun observeById(id: String): Flow<NoteEntity?> = dao.observeById(id)

    suspend fun findById(id: String): NoteEntity? = dao.findById(id)

    /** 标题或正文模糊搜索（关键词为空时退回全量列表）。 */
    fun search(query: String): Flow<List<NoteEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(searchPattern(query))

    /** 新建空备忘，返回新 id。 */
    suspend fun create(content: String = ""): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            NoteEntity(
                id = id,
                title = NoteTextUtils.titleOf(content),
                content = content,
                createdAt = now,
                updatedAt = now,
            ),
        )
        id
    }

    /**
     * 保存：标题始终由正文首行派生，[updatedAt] 只有在内容真正变化时才刷新，
     * 否则「自动保存」会把列表顺序搅乱。
     */
    suspend fun save(note: NoteEntity) = withContext(Dispatchers.IO) {
        val existing = dao.findById(note.id)
        val contentChanged = existing?.content != note.content
        dao.upsert(
            note.copy(
                title = NoteTextUtils.titleOf(note.content),
                updatedAt = if (contentChanged || existing == null) {
                    System.currentTimeMillis()
                } else {
                    existing.updatedAt
                },
            ),
        )
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        dao.setPinned(id, pinned)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun count(): Int = dao.count()

    companion object {
        /**
         * 把用户输入转成 LIKE 模式：转义 `\` `%` `_` 三个特殊字符，
         * 两端补 `%`，配合 DAO 里的 `ESCAPE '\'` 使用。
         */
        fun searchPattern(query: String): String {
            val escaped = query.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
