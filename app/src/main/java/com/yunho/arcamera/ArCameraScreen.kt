package com.yunho.arcamera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
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

        LaunchedEffect(childNodes.size) {
            if (childNodes.isNotEmpty()) {
                val model = childNodes.first() as ModelNode

                scope.launch {
                    model.playAnimation(
                        animationIndex = Emotion.IDLE.value,
                        speed = 1f,
                        loop = true
                    )
                }
            }
        }

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

                                val pose = anchor.pose
                                val modelNode = ModelNode(
                                    modelInstance = modelLoader.createModelInstance("models/BoothScene.glb"),
                                    scaleToUnits = 0.5f,
                                    centerOrigin = Position(pose.tx(), pose.qy(), pose.tz()),
                                    autoAnimate = false
                                ).apply {
                                    isEditable = true
                                }
                                childNodes += modelNode
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

        AnimationMenu(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding(),
            onHappy = {
                if (childNodes.isEmpty()) return@AnimationMenu

                val model = childNodes.first() as ModelNode

                scope.launch {
                    model.playAnimationAndResetToIdle(Emotion.HAPPY)
                }
            },
            onSad = {
                if (childNodes.isEmpty()) return@AnimationMenu

                val model = childNodes.first() as ModelNode

                scope.launch {
                    model.playAnimationAndResetToIdle(Emotion.SAD)
                }
            }
        )

        CaptureButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 100.dp),
            onCapture = {
                arSceneView?.let {
                    scope.launch {
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
private fun AnimationMenu(
    onHappy: () -> Unit,
    onSad: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Row(
        modifier = modifier
    ) {
        AnimationButton(
            text = "happy",
            onAnimate = {
                onHappy()
            }
        )

        Spacer(Modifier.width(20.dp))

        AnimationButton(
            text = "sad",
            onAnimate = {
                onSad()
            }
        )
    }
}

