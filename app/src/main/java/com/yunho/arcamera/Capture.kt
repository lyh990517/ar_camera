package com.yunho.arcamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

fun byteArrayToBitmap(byteArray: ByteArray): Bitmap? {
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}

fun handleSnapshot(arSceneView: ARSceneView) = callbackFlow {
    val bitmap =
        Bitmap.createBitmap(
            arSceneView.width,
            arSceneView.height,
            Bitmap.Config.ARGB_8888,
        )

    val listener =
        PixelCopy.OnPixelCopyFinishedListener { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                val byteStream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
                val byteArray = byteStream.toByteArray()
                trySend(
                    byteArray
                )
            }
        }

    PixelCopy.request(
        arSceneView,
        bitmap,
        listener,
        Handler(Looper.getMainLooper()),
    )

    awaitClose { }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Image_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        resolver.openOutputStream(it).use { outputStream ->
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Toast.makeText(context, "Image saved to gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    } ?: run {
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}