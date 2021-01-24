package ru.magzyumov.contentmanager.filemanager

import android.annotation.TargetApi
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import ru.magzyumov.contentmanager.Constants.StringPattern.Companion.FILE_TIMESTAMP_PATTERN
import ru.magzyumov.contentmanager.enums.Content
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class StorageManager @Inject constructor(
    private val application: Application
) {

    /**
     * Create image file in directory of pictures
     *
     * @param content
     * @return
     */
    fun createFile(content: Content): File? {
        // Create an image file name
        val timeStamp = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.getDefault()).format(Date())
        val type = if (content == Content.IMAGE) ".jpg" else ".mp4"
        val imageFileName = "IMAGE_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        var image: File? = null
        try {
            image = File.createTempFile(
                imageFileName,  /* prefix */
                type,  /* suffix */
                storageDir /* directory */
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return image
    }

    fun getFileUriFromContentUri(cameraPicUri: Uri?): Uri? {
        var cursor: Cursor? = null
        return try {
            if (cameraPicUri != null
                    && cameraPicUri.toString().startsWith("content")) {
                val proj = arrayOf(MediaStore.Images.Media.DATA)
                cursor = application.contentResolver.query(cameraPicUri, proj, null, null, null)
                cursor?.let {
                    it.moveToFirst()
                    // This will actually give you the file path location of the image.
                    val largeImagePath = it.getString(
                        it
                            .getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA)
                    )
                    return Uri.fromFile(File(largeImagePath))
                }
            }
            cameraPicUri
        } catch (e: Exception) {
            cameraPicUri
        } finally {
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun getFilePath(originalPath: String): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        val uri = Uri.parse(originalPath)
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(application, uri)) {
            // ExternalStorageProvider
            if (isFileDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                )
                return getFileData(contentUri, null, null)
            } else if (isFileMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                when (type) {
                    "image" -> {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    "video" -> {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    "audio" -> {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getFileData(contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getFileData(uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    fun getFileData(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        try {
            cursor = application.contentResolver.query(
                uri, projection, selection, selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isFileDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isFileMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    fun getFileFromContentProvider(uri: String): String {
        var uri = uri
        var inputStream: BufferedInputStream? = null
        var outStream: BufferedOutputStream? = null
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        try {
            val localFilePath: String? = generateFileName(uri)
            parcelFileDescriptor = application
                .contentResolver.openFileDescriptor(Uri.parse(uri), "r")

            val fileDescriptor = parcelFileDescriptor?.fileDescriptor
            inputStream = BufferedInputStream(FileInputStream(fileDescriptor))
            val reader = BufferedInputStream(inputStream)
            outStream = BufferedOutputStream(FileOutputStream(localFilePath))
            val buf = ByteArray(2048)
            var len: Int
            while (reader.read(buf).also { len = it } > 0) {
                outStream.write(buf, 0, len)
            }
            outStream.flush()
            localFilePath?.let { uri = it }
        } catch (e: IOException) {
            return uri
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                try {
                    parcelFileDescriptor?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            try {
                outStream?.flush()
                outStream?.close()
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return uri
    }

    fun generateFileName(file: String): String? {
        var fileName = UUID.randomUUID().toString() + "." + guessFileExtensionFromUrl(file)
        var probableFileName = fileName
        val directory: File? = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        var probableFile = File(directory?.absolutePath + File.separator + probableFileName)
        var counter = 0
        while (probableFile.exists()) {
            counter++
            probableFileName = if (fileName.contains(".")) {
                val indexOfDot = fileName.lastIndexOf(".")
                fileName.substring(0, indexOfDot - 1) + "-" + counter + "." + fileName.substring(
                    indexOfDot + 1
                )
            } else {
                "$fileName($counter)"
            }
            probableFile = File(directory?.absolutePath + File.separator + probableFileName)
        }
        fileName = probableFileName
        return (directory?.absolutePath + File.separator + fileName)
    }

    // Guess File extension from the file name
    private fun guessFileExtensionFromUrl(url: String): String? {
        val contentResolver: ContentResolver = application.contentResolver
        val mime = MimeTypeMap.getSingleton()
        val type = mime.getExtensionFromMimeType(contentResolver.getType(Uri.parse(url)))
        contentResolver.getType(Uri.parse(url))
        return type
    }

    // Try to get a local copy if available
    fun getAbsolutePathIfAvailable(uri: String): String {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
        var originalPath: String = if (uri.startsWith("content://com.android.gallery3d.provider")) {
            Uri.parse(uri.replace("com.android.gallery3d", "com.google.android.gallery3d")).toString()
        } else {
            uri
        }
        // Try to see if there's a cached local copy that is available
        if (uri.startsWith("content://")) {
            try {
                val cursor: Cursor? = application.contentResolver.query(
                    Uri.parse(uri), projection, null, null, null
                )
                cursor?.moveToFirst()
                try {
                    // Samsung Bug
                    if (!uri.contains("com.sec.android.gallery3d.provider")) {
                        val path = cursor?.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                        if (path != null) {
                            originalPath = path
                        }
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
                cursor?.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        // Check if DownloadsDocument in which case, we can get the local copy by using the content provider
        if (originalPath!!.startsWith("content:") && isFileDownloadsDocument(Uri.parse(originalPath))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getFilePath(originalPath)?.let { originalPath = it }
            }
        }
        return originalPath
    }
}