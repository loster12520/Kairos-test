package dev.kairos

/**
 * Marks API that is usable but not frozen yet.
 *
 * Everything without this annotation is part of the 1.0 contract (see
 * `docs/contracts/public-api.md`); experimental API may change in a minor release, with a note in
 * the migration guide. Opt in explicitly:
 *
 * ```
 * @OptIn(ExperimentalKairosApi::class)
 * ```
 */
@RequiresOptIn(
    message = "Kairos experimental API: usable, but the shape can change before 1.0",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
annotation class ExperimentalKairosApi
