package ru.magzyumov.contentmanager

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import ru.magzyumov.contentmanager.Constants.StringPattern.Companion.TIMESTAMP_PATTERN
import ru.magzyumov.contentmanager.Constants.StringPattern.Companion.UTC_TIME_ZONE
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


/**
 * Converts a Date object to a string representation.
 */
fun Date?.formatToString(): String {
    val result = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.getDefault())
    result.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE)
    return result.format(this ?: Date())
}

/**
 * Converts a string representation of a date to its respective Date object.
 */
fun String.formatToDate(): Date {
    var result: Date = Date()
    SimpleDateFormat(TIMESTAMP_PATTERN, Locale.getDefault()).parse(this)?.let {
        result = it
    }
    return result
}

/**
 * Some devices return wrong rotated image so we can fix it by this method
 */
fun Bitmap.fixImageRotation(uri: Uri) {
    uri.path?.let {
        val pictureFile = File(it)
        try {
            val fos = FileOutputStream(pictureFile)
            val exif = ExifInterface(pictureFile.toString())
            if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equals("6", ignoreCase = true)) {
                this.rotate(90)
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equals("8", ignoreCase = true)) {
                this.rotate(270)
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equals("3", ignoreCase = true)) {
                this.rotate(180)
            }
            val bo = this.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            Log.d("Info", bo.toString() + "")
        } catch (e: FileNotFoundException) {
            Log.d("Info", "File not found: " + e.message)
        } catch (e: IOException) {
            Log.d("TAG", "Error accessing file: " + e.message)
        }
    }
}

fun Bitmap.rotate(degree: Int): Bitmap {
    val w: Int = this.width
    val h: Int = this.height

    val mtx = Matrix()
    mtx.postRotate(degree.toFloat())

    return Bitmap.createBitmap(this, 0, 0, w, h, mtx, true)
}