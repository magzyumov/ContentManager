package ru.magzyumov.contentmanager.enums

/**
 * Content type
 */
enum class Content (
        private val text: String
) {
    VIDEO("video/*"),
    IMAGE("image/*"),
    FILE("*/*");

    override fun toString(): String = text
}