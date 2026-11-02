package dev.kairos.blog

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.newKSqlClient
import java.io.File
import java.sql.Connection

/**
 * SQLite + Jimmer. One connection pool, one schema, a handful of type safe queries - the demo is
 * about the rendering pipeline, not the persistence layer.
 */
object Db {

    lateinit var sql: KSqlClient
        private set

    private lateinit var dataSource: HikariDataSource

    fun start(databaseFile: File) {
        databaseFile.parentFile?.mkdirs()
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:" + databaseFile.absolutePath
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            isAutoCommit = true
        })
        dataSource.connection.use(::createSchema)
        sql = newKSqlClient {
            setConnectionManager(ConnectionManager.simpleConnectionManager(dataSource))
            setDialect(SQLiteDialect())
        }
        seed()
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "create table if not exists post (" +
                    "id integer primary key autoincrement, title text not null, slug text not null, " +
                    "body text not null, created_at text not null)"
            )
            statement.executeUpdate(
                "create table if not exists comment (" +
                    "id integer primary key autoincrement, post_id integer not null, author text not null, " +
                    "body text not null, created_at text not null)"
            )
        }
    }

    private fun seed() {
        if (allPosts().isNotEmpty()) return
        insertPost(
            "Hello, Kairos",
            "hello-kairos",
            """
            # A blog rendered by Kotlin

            Every page on this site is a Kotlin function that returns a node tree. The server
            renders it to an **HTML string**, htmx swaps fragments in place, and every style is a
            class the compiler computed at build time.

            - pages are ordinary functions, not templates
            - fragments are just smaller trees
            - the client bundle only moves class names

            > The interesting part is what you do **not** see: there is no runtime style engine.
            """.trimIndent(),
        )
        insertPost(
            "One block, one class",
            "one-block-one-class",
            """
            ## Grouping, then extraction

            A style block becomes one class, and a declaration that appears in more than one block
            is lifted into a class of its own:

            - the block class keeps its residual declarations
            - the shared class carries the repeated one
            - the element simply gets both class names

            That is why the stylesheet never contains the same property twice.
            """.trimIndent(),
        )
        insertPost(
            "Reactive where it matters",
            "reactive-where-it-matters",
            """
            ### Signals, not re-renders

            The client bundle wires two behaviours:

            1. typing in the filter box hides the cards that do not match
            2. the theme button flips a class on the html element

            Both are one signal plus one effect. There is no virtual DOM and no style value is
            computed in JavaScript - only class names and text change.

            The colours themselves come from CSS variables, so a theme switch is a single class.
            """.trimIndent(),
        )
    }

    fun insertPost(title: String, slug: String, body: String): Long {
        val draft: Post = Post {
            this.title = title
            this.slug = slug
            this.body = body
            this.createdAt = nowIso()
        }
        return sql.save(draft, SaveMode.INSERT_ONLY).modifiedEntity.id
    }

    fun insertComment(postId: Long, author: String, body: String): Long {
        val draft: Comment = Comment {
            this.postId = postId
            this.author = author
            this.body = body
            this.createdAt = nowIso()
        }
        return sql.save(draft, SaveMode.INSERT_ONLY).modifiedEntity.id
    }

    private fun allPosts(): List<Post> = sql.createQuery(Post::class) { select(table) }.execute()

    /** Newest first by default; `sort = "comments"` orders by comment count then recency. */
    fun posts(sort: String = "newest"): List<Post> {
        val all = allPosts()
        return when (sort) {
            "comments" -> {
                val counts = commentCounts(all.map { it.id })
                all.sortedWith(compareByDescending<Post> { counts[it.id] ?: 0 }.thenByDescending { it.createdAt })
            }
            else -> all.sortedByDescending { it.createdAt }
        }
    }

    fun updatePost(id: Long, title: String, body: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("update post set title = ?, body = ? where id = ?").use { statement ->
                statement.setString(1, title)
                statement.setString(2, body)
                statement.setLong(3, id)
                statement.executeUpdate()
            }
        }
    }

    fun deletePost(id: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("delete from comment where post_id = ?").use { statement ->
                statement.setLong(1, id)
                statement.executeUpdate()
            }
            connection.prepareStatement("delete from post where id = ?").use { statement ->
                statement.setLong(1, id)
                statement.executeUpdate()
            }
        }
    }

    fun post(id: Long): Post? = allPosts().firstOrNull { it.id == id }

    fun comments(postId: Long): List<Comment> =
        sql.createQuery(Comment::class) { where(table.postId eq postId); select(table) }
            .execute()
            .sortedBy { it.createdAt }

    fun commentCounts(postIds: List<Long>): Map<Long, Int> =
        sql.createQuery(Comment::class) { select(table) }
            .execute()
            .filter { it.postId in postIds }
            .groupingBy { it.postId }
            .eachCount()

    private fun nowIso(): String = java.time.LocalDateTime.now().toString()
}
