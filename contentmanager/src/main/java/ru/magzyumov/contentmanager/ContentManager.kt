package ru.magzyumov.contentmanager

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ru.magzyumov.contentmanager.Constants.RequestCode.Companion.CONTENT_PICKER
import ru.magzyumov.contentmanager.Constants.RequestCode.Companion.CONTENT_TAKE
import ru.magzyumov.contentmanager.Constants.RequestCode.Companion.CONTENT_VIDEO
import ru.magzyumov.contentmanager.Constants.RequestCode.Companion.PERMISSION_REQUEST_CODE
import ru.magzyumov.contentmanager.Constants.StatesCode.Companion.CAMERA_PIC_URI_STATE
import ru.magzyumov.contentmanager.Constants.StatesCode.Companion.DATE_CAMERA_INTENT_STARTED_STATE
import ru.magzyumov.contentmanager.Constants.StatesCode.Companion.PHOTO_URI_STATE
import ru.magzyumov.contentmanager.Constants.StatesCode.Companion.ROTATE_X_DEGREES_STATE
import ru.magzyumov.contentmanager.Constants.StatesCode.Companion.SAVED_CONTENT_STATE
import ru.magzyumov.contentmanager.Constants.StatesCode.Companion.SAVED_TASK_STATE
import ru.magzyumov.contentmanager.Constants.StatesCode.Companion.TARGET_FILE_STATE
import ru.magzyumov.contentmanager.enums.Content
import ru.magzyumov.contentmanager.enums.Take
import ru.magzyumov.contentmanager.filemanager.StorageManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject


class ContentManager (
    private val activity: Activity,
    private val pickContentListener: PickContentListener,
    private val fragment: Fragment? = null
) {

    /**
     * Date and time the camera intent was started.
     */
    private var dateCameraIntentStarted: Date? = null

    /**
     * Default location where we want the photo to be ideally stored.
     */
    private var preDefinedCameraUri: Uri? = null
    private var photoUri: Uri? = null
    private var videoUri: Uri? = null

    /**
     * Potential 3rd location of photo data.
     */
    private var photoUriIn3rdLocation: Uri? = null
    private var videoUriIn3rdLocation: Uri? = null

    /**
     * Orientation of the retrieved photo.
     */
    private var rotateXDegrees = 0

    /**
     * Result target file
     */
    private var targetFile: File? = null

    /**
     * For monitor the load process
     */
    private var handler: Handler? = null
    private val progressPercent = 0


    private var savedTask = 0
    private var savedContent: Content? = null

    /**
     * Storage manager
     */
    @Inject
    lateinit var storageManager: StorageManager

    /**
     * Constructors
     */
    init {
        handler = Handler(Looper.getMainLooper())
    }


    /**
     * Need to call in onSaveInstanceState method of activity
     */
    fun onSaveInstanceState(savedInstanceState: Bundle) {
        dateCameraIntentStarted?.let {
            savedInstanceState.putString(DATE_CAMERA_INTENT_STARTED_STATE, it.formatToString())
        }

        preDefinedCameraUri?.let { savedInstanceState.putString(CAMERA_PIC_URI_STATE, it.toString()) }
        photoUri?.let {  savedInstanceState.putString(PHOTO_URI_STATE, it.toString()) }
        targetFile?.let { savedInstanceState.putSerializable(TARGET_FILE_STATE, it) }
        savedContent?.let { savedInstanceState.putSerializable(SAVED_CONTENT_STATE, it) }

        savedInstanceState.putInt(SAVED_TASK_STATE, savedTask)
        savedInstanceState.putInt(ROTATE_X_DEGREES_STATE, rotateXDegrees)
    }

    /**
     * Call to reinitialize the helpers instance state.
     * Need to call in onRestoreInstanceState method of activity
     */
    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(DATE_CAMERA_INTENT_STARTED_STATE)) {
                savedInstanceState.getString(DATE_CAMERA_INTENT_STARTED_STATE)?.let {
                    dateCameraIntentStarted = it.formatToDate()
                }
            }
            if (savedInstanceState.containsKey(CAMERA_PIC_URI_STATE)) {
                preDefinedCameraUri = Uri.parse(savedInstanceState.getString(CAMERA_PIC_URI_STATE))
            }
            if (savedInstanceState.containsKey(PHOTO_URI_STATE)) {
                photoUri = Uri.parse(savedInstanceState.getString(PHOTO_URI_STATE))
            }
            if (savedInstanceState.containsKey(ROTATE_X_DEGREES_STATE)) {
                rotateXDegrees =
                    savedInstanceState.getInt(ROTATE_X_DEGREES_STATE)
            }
            if (savedInstanceState.containsKey(TARGET_FILE_STATE)) {
                targetFile = savedInstanceState.getSerializable(TARGET_FILE_STATE) as File
            }
            if (savedInstanceState.containsKey(SAVED_CONTENT_STATE)) {
                savedContent = savedInstanceState.getSerializable(SAVED_CONTENT_STATE) as Content
            }
            if (savedInstanceState.containsKey(SAVED_TASK_STATE)) {
                savedTask = savedInstanceState.getInt(SAVED_TASK_STATE)
            }
        }
    }

    /**
     * Need to call in onActivityResult method of activity or fragment
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if(resultCode == Activity.RESULT_OK) {
            if (requestCode == CONTENT_PICKER) {
                handleContentData(data)
            }

            if (requestCode == CONTENT_TAKE) {
                onCameraIntentResult(data)
            }

            if (requestCode == CONTENT_VIDEO) {
                onVideoIntentResult(data)
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            pickContentListener.onCanceled()
        } else {
            pickContentListener.onCanceled()
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty()) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when (savedTask) {
                        CONTENT_PICKER -> pickContent(savedContent!!)
                        CONTENT_TAKE -> takePhoto()
                    }
                }
            }
        }
    }
    /**
     * Pick image or video content from storage or google acc
     *
     * @param content image or video
     */
    fun pickContent(content: Content) {
        savedTask = CONTENT_PICKER
        savedContent = content
        if (isStoragePermissionGranted(activity, fragment)) {
            targetFile = storageManager.createFile(content)
            if (Build.VERSION.SDK_INT < 19) {
                val photoPickerIntent = Intent(Intent.ACTION_PICK)
                photoPickerIntent.type = content.toString()
                if (fragment == null) {
                    activity.startActivityForResult(photoPickerIntent, CONTENT_PICKER)
                } else {
                    fragment.startActivityForResult(photoPickerIntent, CONTENT_PICKER)
                }
            } else {
                val photoPickerIntent = Intent(Intent.ACTION_GET_CONTENT)
                photoPickerIntent.type = content.toString()
                photoPickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                photoPickerIntent.addCategory(Intent.CATEGORY_OPENABLE)
                if (photoPickerIntent.resolveActivity(activity!!.packageManager) != null) {
                    if (fragment == null) {
                        activity?.startActivityForResult(photoPickerIntent, CONTENT_PICKER)
                    } else {
                        fragment?.startActivityForResult(photoPickerIntent, CONTENT_PICKER)
                    }
                }
            }
        }
    }

    fun takeContent(take: Take) {
        savedTask = CONTENT_TAKE
        if (isStoragePermissionGranted(activity!!, fragment!!)) {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                try {
                    val setPreDefinedCameraUri: Boolean = isSetPreDefinedCameraUri()
                    dateCameraIntentStarted = Date()
                    val intentType = when (take){
                        Take.IMAGE -> MediaStore.ACTION_IMAGE_CAPTURE
                        Take.VIDEO -> MediaStore.ACTION_VIDEO_CAPTURE
                    }
                    val intent = Intent(intentType)
                    if (setPreDefinedCameraUri) {
                        val filename = System.currentTimeMillis().toString() + take.toString()
                        val values = ContentValues()
                        values.put(MediaStore.Images.Media.TITLE, filename)
                        preDefinedCameraUri = activity?.contentResolver?.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        )
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, preDefinedCameraUri)
                    }
                    if (fragment == null) {
                        activity?.startActivityForResult(intent, CONTENT_TAKE)
                    } else {
                        fragment?.startActivityForResult(intent, CONTENT_TAKE)
                    }
                } catch (e: ActivityNotFoundException) {
                    pickContentListener.onError(e.localizedMessage.orEmpty())
                }
            } else {
                pickContentListener.onError("")
            }
        }
    }

    fun takePhoto() {
        savedTask = CONTENT_TAKE
        if (isStoragePermissionGranted(activity, fragment)) {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                try {
                    val setPreDefinedCameraUri: Boolean = isSetPreDefinedCameraUri()
                    dateCameraIntentStarted = Date()
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (setPreDefinedCameraUri) {
                        val filename = System.currentTimeMillis().toString() + ".jpg"
                        val values = ContentValues()
                        values.put(MediaStore.Images.Media.TITLE, filename)
                        preDefinedCameraUri = activity!!.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        )
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, preDefinedCameraUri)
                    }
                    if (fragment == null) {
                        activity.startActivityForResult(intent, CONTENT_TAKE)
                    } else {
                        fragment.startActivityForResult(intent, CONTENT_TAKE)
                    }
                } catch (e: ActivityNotFoundException) {
                    pickContentListener.onError(e.localizedMessage.orEmpty())
                }
            } else {
                pickContentListener.onError("")
            }
        }
    }

    fun takeVideo() {
        savedTask = CONTENT_VIDEO
        if (isStoragePermissionGranted(activity, fragment)) {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                try {
                    val setPreDefinedCameraUri: Boolean = isSetPreDefinedCameraUri()
                    dateCameraIntentStarted = Date()
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    if (setPreDefinedCameraUri) {
                        val filename = System.currentTimeMillis().toString() + ".mp4"
                        val values = ContentValues()
                        values.put(MediaStore.Video.Media.TITLE, filename)
                        preDefinedCameraUri = activity.contentResolver?.insert(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                        )
                        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 5)
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, preDefinedCameraUri)
                    }
                    if (fragment == null) {
                        activity.startActivityForResult(intent, CONTENT_VIDEO)
                    } else {
                        fragment.startActivityForResult(intent, CONTENT_VIDEO)
                    }
                } catch (e: ActivityNotFoundException) {
                    pickContentListener.onError("")
                }
            } else {
                pickContentListener.onError("")
            }
        }
    }

    /**
     * Check device model and return is need to set predefined camera uri
     */
    private fun isSetPreDefinedCameraUri(): Boolean {
        var setPreDefinedCameraUri = false

        // NOTE: Do NOT SET: intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPicUri)
        // on Samsung Galaxy S2/S3/.. for the following reasons:
        // 1.) it will break the correct picture orientation
        // 2.) the photo will be stored in two locations (the given path and, additionally, in the MediaStore)
        val manufacturer = Build.MANUFACTURER.toLowerCase(Locale.ENGLISH)
        val model = Build.MODEL.toLowerCase(Locale.ENGLISH)
        val buildType = Build.TYPE.toLowerCase(Locale.ENGLISH)
        val buildDevice = Build.DEVICE.toLowerCase(Locale.ENGLISH)
        val buildId = Build.ID.toLowerCase(Locale.ENGLISH)
        //	String sdkVersion = android.os.Build.VERSION.RELEASE.toLowerCase(Locale.ENGLISH)
        if (!manufacturer.contains("samsung") && !manufacturer.contains("sony")) {
            setPreDefinedCameraUri = true
        }
        if (manufacturer.contains("samsung") && model.contains("galaxy nexus")) { //TESTED
            setPreDefinedCameraUri = true
        }
        if (manufacturer.contains("samsung") && model.contains("gt-n7000") && buildId.contains("imm76l")) { //TESTED
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("ariesve")) {  //TESTED
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("crespo")) {   //TESTED
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("gt-i9100")) { //TESTED
            setPreDefinedCameraUri = true
        }

        ///////////////////////////////////////////////////////////////////////////
        // TEST
        if (manufacturer.contains("samsung") && model.contains("sgh-t999l")) { //T-Mobile LTE enabled Samsung S3
            setPreDefinedCameraUri = true
        }
        if (buildDevice.contains("cooper")) {
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("t0lte")) {
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("kot49h")) {
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("t03g")) {
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("gt-i9300")) {
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("gt-i9195")) {
            setPreDefinedCameraUri = true
        }
        if (buildType.contains("userdebug") && buildDevice.contains("xperia u")) {
            setPreDefinedCameraUri = true
        }

        ///////////////////////////////////////////////////////////////////////////
        return setPreDefinedCameraUri
    }

    /**
     * Process result of camera intent
     */
    private fun onCameraIntentResult(intent: Intent) {
        var myCursor: Cursor? = null
        var dateOfPicture: Date? = null
        try {
            // Create a Cursor to obtain the file Path for the large image
            val largeFileProjection = arrayOf(
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.ORIENTATION,
                MediaStore.Images.ImageColumns.DATE_TAKEN
            )
            val largeFileSort = MediaStore.Images.ImageColumns._ID + " DESC"
            myCursor = activity?.contentResolver?.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                largeFileProjection,
                null, null,
                largeFileSort
            )

            myCursor?.let { cursor ->
                cursor.moveToFirst()
                if (!cursor.isAfterLast) {
                    // This will actually give you the file path location of the image.
                    val largeImagePath = cursor.getString(
                        cursor
                            .getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA)
                    )
                    photoUri = Uri.fromFile(File(largeImagePath))
                    if (photoUri != null) {
                        dateOfPicture = Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)))
                        if (dateOfPicture != null && dateOfPicture!!.after(dateCameraIntentStarted)) {
                            rotateXDegrees = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION))
                        } else {
                            photoUri = null
                        }
                    }
                    if (cursor.moveToNext() && !cursor.isAfterLast) {
                        val largeImagePath3rdLocation = cursor.getString(
                            cursor
                                .getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA)
                        )
                        val dateOfPicture3rdLocation = Date(
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    MediaStore.Images.ImageColumns.DATE_TAKEN
                                )
                            )
                        )
                        if (dateOfPicture3rdLocation != null && dateOfPicture3rdLocation.after(
                                dateCameraIntentStarted
                            )) {
                            photoUriIn3rdLocation = Uri.fromFile(File(largeImagePath3rdLocation))
                        }
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            if (myCursor != null && !myCursor.isClosed) {
                myCursor.close()
            }
        }
        if (photoUri == null) {
            try {
                photoUri = intent.data
            } catch (e: Exception) {
            }
        }
        if (photoUri == null) {
            photoUri = preDefinedCameraUri
        }
        try {
            if (photoUri != null && File(photoUri!!.path).length() <= 0) {
                if (preDefinedCameraUri != null) {
                    val tempUri: Uri = photoUri as Uri
                    photoUri = preDefinedCameraUri
                    preDefinedCameraUri = tempUri
                }
            }
        } catch (e: Exception) {
        }
        photoUri = storageManager.getFileUriFromContentUri(photoUri)
        preDefinedCameraUri = storageManager.getFileUriFromContentUri(preDefinedCameraUri)
        try {
            if (photoUriIn3rdLocation != null) {
                if (photoUriIn3rdLocation == photoUri || photoUriIn3rdLocation == preDefinedCameraUri) {
                    photoUriIn3rdLocation = null
                } else {
                    photoUriIn3rdLocation = storageManager.getFileUriFromContentUri(
                        photoUriIn3rdLocation
                    )
                }
            }
        } catch (e: Exception) {
        }
        if (photoUri != null) {
            pickContentListener.onContentLoaded( photoUri as Uri, Content.IMAGE.toString() )
        } else {
            pickContentListener.onError("")
        }
    }

    /**
     * Process result of video intent
     */
    private fun onVideoIntentResult(intent: Intent) {
        var myCursor: Cursor? = null
        var dateOfVideo: Date? = null
        try {
            // Create a Cursor to obtain the file Path for the large video
            val largeFileProjection = arrayOf(
                MediaStore.Video.VideoColumns._ID,
                MediaStore.Video.VideoColumns.DATA,
                MediaStore.Video.VideoColumns.ORIENTATION,
                MediaStore.Video.VideoColumns.DATE_TAKEN
            )
            val largeFileSort = MediaStore.Video.VideoColumns._ID + " DESC"
            myCursor = activity!!.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                largeFileProjection,
                null, null,
                largeFileSort
            )
            myCursor!!.moveToFirst()
            if (!myCursor.isAfterLast) {
                // This will actually give you the file path location of the image.
                val largeVideoPath = myCursor.getString(
                    myCursor
                        .getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATA)
                )
                videoUri = Uri.fromFile(File(largeVideoPath))
                if (videoUri != null) {
                    dateOfVideo =
                        Date(myCursor.getLong(myCursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_TAKEN)))
                    if (dateOfVideo != null && dateOfVideo.after(dateCameraIntentStarted)) {
                        rotateXDegrees = myCursor.getInt(
                            myCursor
                                .getColumnIndexOrThrow(MediaStore.Video.VideoColumns.ORIENTATION)
                        )
                    } else {
                        videoUri = null
                    }
                }
                if (myCursor.moveToNext() && !myCursor.isAfterLast) {
                    val largeVideoPath3rdLocation = myCursor.getString(
                        myCursor
                            .getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATA)
                    )
                    val dateOfVideo3rdLocation = Date(
                        myCursor.getLong(
                            myCursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_TAKEN)
                        )
                    )
                    if (dateOfVideo3rdLocation != null && dateOfVideo3rdLocation.after(
                            dateCameraIntentStarted
                        )
                    ) {
                        videoUriIn3rdLocation = Uri.fromFile(File(largeVideoPath3rdLocation))
                    }
                }
            }
        } catch (e: java.lang.Exception) {
        } finally {
            if (myCursor != null && !myCursor.isClosed) {
                myCursor.close()
            }
        }
        if (videoUri == null) {
            try {
                videoUri = intent.data
            } catch (e: java.lang.Exception) {
            }
        }
        if (videoUri == null) {
            videoUri = preDefinedCameraUri
        }
        try {
            if (videoUri != null && File(videoUri!!.getPath()).length() <= 0) {
                if (preDefinedCameraUri != null) {
                    val tempUri: Uri = videoUri as Uri
                    videoUri = preDefinedCameraUri
                    preDefinedCameraUri = tempUri
                }
            }
        } catch (e: java.lang.Exception) {
        }
        videoUri = storageManager.getFileUriFromContentUri(videoUri)
        preDefinedCameraUri = storageManager.getFileUriFromContentUri(preDefinedCameraUri)
        try {
            if (videoUriIn3rdLocation != null) {
                if (videoUriIn3rdLocation == photoUri || videoUriIn3rdLocation == preDefinedCameraUri) {
                    videoUriIn3rdLocation = null
                } else {
                    videoUriIn3rdLocation = storageManager.getFileUriFromContentUri(videoUriIn3rdLocation)
                }
            }
        } catch (e: java.lang.Exception) {
        }
        if (videoUri != null) {
            pickContentListener.onContentLoaded(videoUri!!, Content.VIDEO.toString())
        } else {
            pickContentListener.onError("")
        }
    }


    /**
     * Async load content data
     *
     * @param data result intent
     */
    private fun handleContentData(data: Intent?) {
        if (data != null) {
            if (savedContent != Content.FILE) {
                handleMediaContent(data)
            } else {
                handleFileContent(data)
            }
        } else {
            handler?.post { pickContentListener.onError("Data null") }
        }
    }

    private fun handleMediaContent(data: Intent) {
        pickContentListener.onStartContentLoading()

        Thread {
            try {
                val contentVideoUri = data.data as Uri
                val inputStream = activity?.contentResolver?.openInputStream(contentVideoUri) as FileInputStream
                if (targetFile == null) {
                    targetFile = storageManager.createFile(savedContent as Content)
                }
                val out = FileOutputStream(targetFile)
                val inChannel = inputStream.channel
                val outChannel = out.channel
                inChannel.transferTo(0, inChannel.size(), outChannel)
                inputStream.close()
                out.close()
                handler?.post {
                    pickContentListener.onContentLoaded(
                        Uri.fromFile(targetFile), savedContent.toString()
                    )
                }
            } catch (e: java.lang.Exception) {
                handler?.post { pickContentListener.onError(e.localizedMessage.orEmpty()) }
            }
        }.start()
    }

    private fun handleFileContent(intent: Intent) {
        val uris: MutableList<String> = ArrayList()
        intent.dataString?.let {
            uris.add(it)
        } ?: run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                intent.clipData?.let {
                    for (i in 0 until it.itemCount) {
                        val item = it.getItemAt(i)
                        Log.d("TAG", "Item [" + i + "]: " + item.uri.toString())
                        uris.add(item.uri.toString())
                    }
                }
            }
        }

        if (intent.hasExtra("uris")) {
            val paths = intent.getParcelableArrayListExtra<Uri>("uris")
            paths?.indices?.forEach {
                uris.add(paths[it].toString())
            } ?: return
        }

        //TODO Handle multiple file choose
        processFile(uris[0])
    }

    private fun processFile(queryUri: String) {
        pickContentListener.onStartContentLoading()

        Thread {
            var originalPath = ""
            var uri = queryUri
            if (uri.startsWith("file://") || uri.startsWith("/")) {
                originalPath = sanitizeUri(uri)
            } else if (uri.startsWith("content:")) {
                originalPath = storageManager.getAbsolutePathIfAvailable(uri)
            }
            uri = originalPath
            // Still content:: Try ContentProvider stream import
            if (uri.startsWith("content:")) {
                originalPath = storageManager.getFileFromContentProvider(originalPath)
            }

            // Check for URL Encoded file paths
            try {
                val decodedURL = Uri.parse(Uri.decode(originalPath)).toString()
                if (decodedURL != originalPath) {
                    originalPath = decodedURL
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            val finalOriginalPath = originalPath
            handler?.post {
                pickContentListener.onContentLoaded(
                    Uri.parse(finalOriginalPath), savedContent.toString()
                )
            }
        }.start()
    }

    // If starts with file: (For some content providers, remove the file prefix)
    private fun sanitizeUri(uri: String): String {
        return if (uri.startsWith("file://")) {
            uri.substring(7)
        } else uri
    }


    //For fragments
    private fun isStoragePermissionGranted(activity: Activity, fragment: Fragment?): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                if (fragment == null) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE
                    )
                } else {
                    fragment.requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        PERMISSION_REQUEST_CODE
                    )
                }
                false
            }
        } else {
            true
        }
    }
}