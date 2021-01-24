package ru.magzyumov.contentmanager

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import ru.magzyumov.contentmanager.enums.Content

class MainActivity : AppCompatActivity(), PickContentListener {
    private val TAG = javaClass.simpleName

    private var contentManager: ContentManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contentManager = ContentManager(this, this )

        findViewById<Button>(R.id.btnPickContentImage).setOnClickListener {
            contentManager?.pickContent(Content.IMAGE)
        }
        findViewById<Button>(R.id.btnPickContentVideo).setOnClickListener {
            contentManager?.pickContent(Content.VIDEO)
        }
        findViewById<Button>(R.id.btnPickContentFile).setOnClickListener {
            contentManager?.pickContent(Content.FILE)
        }
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            contentManager?.takePhoto()
        }
        findViewById<Button>(R.id.btnTakeVideo).setOnClickListener {
            contentManager?.takeVideo()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null){
            contentManager?.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        contentManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onContentLoaded(uri: Uri, contentType: String) {
        Log.e(TAG, "$contentType $uri")
    }

    override fun onStartContentLoading() {
        Log.e(TAG, "onStartContentLoading")
    }

    override fun onError(error: String) {
        Log.e(TAG, "onError: $error")
    }

    override fun onCanceled() {
        Log.e(TAG, "onCanceled")
    }
}