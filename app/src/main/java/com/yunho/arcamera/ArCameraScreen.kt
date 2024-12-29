package com.yunho.arcamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ArCameraScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val scope = rememberCoroutineScope()
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val cameraNode = rememberARCameraNode(engine)
        val childNodes = rememberNodes()
        val view = rememberView(engine)
        val collisionSystem = rememberCollisionSystem(view)
        var modelScale by remember { mutableFloatStateOf(0.5f) }
        var currentAnchor by remember { mutableStateOf<Anchor?>(null) }
        var frame by remember { mutableStateOf<Frame?>(null) }
        var captureResult by remember { mutableStateOf<Bitmap?>(null) }
        var arSceneView by remember { mutableStateOf<ARSceneView?>(null) }

        ARScene(
            modifier = Modifier
                .fillMaxSize(),
            childNodes = childNodes,
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            planeRenderer = false,
            collisionSystem = collisionSystem,
            sessionConfiguration = { session, config ->
                config.depthMode =
                    when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        true -> Config.DepthMode.AUTOMATIC
                        else -> Config.DepthMode.DISABLED
                    }
                config.lightEstimationMode =
                    Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            cameraNode = cameraNode,
            onSessionUpdated = { _, updatedFrame ->
                frame = updatedFrame
                val pose = currentAnchor?.pose
                val list = childNodes.map { node ->
                    node.apply {
                        scale = Scale(modelScale, modelScale, modelScale)
                        pose?.let { position = Position(pose.tx(), pose.ty(), pose.tz()) }
                    }
                }
                childNodes.clear()
                childNodes.addAll(list)
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapUp = { motionEvent, node ->
                    if (node == null) {
                        val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                        hitResults
                            ?.firstOrNull { it.isValid(depthPoint = false, point = false) }
                            ?.createAnchorOrNull()
                            ?.let { anchor ->
                                childNodes.clear()

                                currentAnchor = anchor
                                childNodes += createAnchorNode(
                                    engine = engine,
                                    modelLoader = modelLoader,
                                    anchor = anchor
                                )
                            }
                    }
                },
                onMove = { _, motionEvent, _ ->
                    val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)

                    hitResults
                        ?.firstOrNull { it.isValid(depthPoint = false, point = false) }
                        ?.createAnchorOrNull()
                        ?.let { anchor ->
                            currentAnchor = anchor
                        }
                },
                onScale = { scaleGestureDetector, _, _ ->
                    val scaleFactor = scaleGestureDetector.scaleFactor
                    modelScale = (modelScale * scaleFactor).coerceAtMost(1f).coerceAtLeast(0.1f)
                },
            ),
            onViewCreated = {
                arSceneView = this
            }
        )

        ResetButton(
            modifier = Modifier.Companion
                .align(Alignment.TopEnd)
                .statusBarsPadding(),
            onReset = {
                childNodes.clear()
                currentAnchor = null
                modelScale = 0.5f
            }
        )

        CaptureButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 100.dp),
            onCapture = {
                arSceneView?.let {
                    scope.launch(Dispatchers.IO) {
                        val byteArray = handleSnapshot(it).first()
                        val bitmap = byteArrayToBitmap(byteArray)
                        captureResult = bitmap
                    }
                }
            }
        )

        CaptureResult(
            result = captureResult,
            onBackPressed = {
                captureResult = null
            }
        )
    }
}

@Composable
private fun ResetButton(
    modifier: Modifier,
    onReset: () -> Unit,
) {
    IconButton(
        onClick = {
            onReset()
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "reset",
            tint = Color.White
        )
    }
}

@Composable
private fun CaptureButton(
    modifier: Modifier = Modifier,
    onCapture: () -> Unit,
) {
    FloatingActionButton(
        onClick = { onCapture() },
        containerColor = Color.White,
        contentColor = Color.Black,
        modifier = modifier
            .size(72.dp)
            .shadow(10.dp, shape = CircleShape)
    ) {
        Icon(
            painter = painterResource(R.drawable.camera),
            contentDescription = "Capture",
            tint = Color.Black,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun SaveButton(
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
) {
    FloatingActionButton(
        onClick = { onSave() },
        containerColor = Color.White,
        contentColor = Color.Black,
        modifier = modifier
            .size(72.dp)
            .shadow(10.dp, shape = CircleShape)
    ) {
        Icon(
            painter = painterResource(R.drawable.download),
            contentDescription = "Save",
            tint = Color.Black,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun CaptureResult(
    result: Bitmap?,
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                result?.let { bitmap ->
                    saveBitmapToGallery(context = context, bitmap = bitmap)
                }
            } else {
                Toast.makeText(context, "Permission denied. Cannot save image.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    )

    result?.let { bitmap ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            SaveButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveBitmapToGallery(context = context, bitmap = bitmap)
                } else {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        saveBitmapToGallery(context = context, bitmap = bitmap)
                    } else {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }
        }
    }
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

private fun byteArrayToBitmap(byteArray: ByteArray): Bitmap? {
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}

private fun handleSnapshot(arSceneView: ARSceneView) = callbackFlow {
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

fun createAnchorNode(
    engine: Engine,
    modelLoader: ModelLoader,
    anchor: Anchor,
): AnchorNode {
    val pose = anchor.pose
    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
    val modelNode = ModelNode(
        modelInstance = modelLoader.createModelInstance("models/halloween.glb"),
        scaleToUnits = 0.05f,
        centerOrigin = Position(pose.tx(), pose.qy(), pose.tz())
    ).apply {
        isEditable = true
    }
    anchorNode.addChildNode(modelNode)
    return anchorNode
}