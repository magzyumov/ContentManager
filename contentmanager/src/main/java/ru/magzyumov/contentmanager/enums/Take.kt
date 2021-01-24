package ru.magzyumov.contentmanager.enums

enum class Take(
    private val text: String
    ) {
        VIDEO(".jpg"),
        IMAGE(".mp4");

        override fun toString(): String = text
}