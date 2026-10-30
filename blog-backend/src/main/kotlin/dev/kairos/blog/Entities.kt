package dev.kairos.blog

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import java.time.LocalDateTime

/**
 * A blog post. Jimmer generates the immutable implementation, the draft used for writes and the
 * table object used for type safe queries.
 */
@Entity
interface Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val title: String
    val slug: String
    val body: String

    /** ISO-8601 text: SQLite has no native timestamp type and text sorts correctly. */
    val createdAt: String
}
