package dev.kairos.blog

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id

@Entity
interface Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    /** Plain foreign key: the blog never needs an association traversal. */
    val postId: Long

    val author: String
    val body: String
    val createdAt: String
}
