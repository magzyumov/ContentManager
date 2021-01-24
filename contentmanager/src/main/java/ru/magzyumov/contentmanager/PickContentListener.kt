package ru.magzyumov.contentmanager

import android.net.Uri

/**
 * Result callback
 */
interface PickContentListener {
    fun onContentLoaded(uri: Uri, contentType: String)
    fun onStartContentLoading()
    fun onError(error: String)
    fun onCanceled()
}