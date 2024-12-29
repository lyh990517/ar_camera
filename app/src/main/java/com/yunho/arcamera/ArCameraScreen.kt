package com.yunho.arcamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
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
        val captureResult = remember { mutableStateOf<Bitmap?>(null) }
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
                        pose?.let {
                            position = Position(
                                pose.tx(), pose.ty(), pose.tz()
                            )
                        }
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
                            ?.firstOrNull()
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
                onMove = { d, m, n ->
                    val hitResults = frame?.hitTest(m.x, m.y)

                    hitResults
                        ?.firstOrNull()
                        ?.createAnchorOrNull()
                        ?.let { anchor ->
                            currentAnchor = anchor
                        }
                },
                onScale = { scaleGestureDetector, motionEvent, node ->
                    val scaleFactor = scaleGestureDetector.scaleFactor
                    modelScale = (modelScale * scaleFactor).coerceAtMost(1f).coerceAtLeast(0.1f)
                },
            ),
            onViewCreated = {
                arSceneView = this
            }
        )
        Menu(
            modifier = Modifier.align(Alignment.BottomCenter),
            onClear = {
                childNodes.clear()
                currentAnchor = null
                modelScale = 0.5f
            },
            onCapture = {
                arSceneView?.let {
                    scope.launch(Dispatchers.IO) {
                        val byteArray = handleSnapshot(it).first()
                        val bitmap = byteArrayToBitmap(byteArray)
                        captureResult.value = bitmap
                    }
                }
            }
        )
        CaptureResult(captureResult)
    }
}

@Composable
private fun Menu(
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
    onCapture: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 100.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(onClick = {
                onClear()
            }) {
                Text("Reset")
            }
            Button(onClick = {
                onCapture()
            }) {
                Text("Capture")
            }
        }
    }
}

@Composable
private fun CaptureResult(captureBitmapState: MutableState<Bitmap?>) {
    captureBitmapState.value?.let { bitmap ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
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