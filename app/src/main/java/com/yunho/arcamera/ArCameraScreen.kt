package com.yunho.arcamera

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView

enum class RotationAxis {
    X, Y, Z
}

@Composable
fun ArCameraScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val cameraNode = rememberARCameraNode(engine)
        val childNodes = rememberNodes()
        val view = rememberView(engine)
        val collisionSystem = rememberCollisionSystem(view)
        var modelScale by remember { mutableStateOf(0.5f) }
        var currentAnchor by remember { mutableStateOf<Anchor?>(null) }
        var rotationMode by remember { mutableStateOf(RotationAxis.X) }
        var rotationDegree by remember { mutableStateOf(Rotation(1f, 1f, 1f)) }
        var frame by remember { mutableStateOf<Frame?>(null) }

        ARScene(
            modifier = Modifier.fillMaxSize(),
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

                        rotation = rotationDegree
                    }
                }
                childNodes.clear()
                childNodes.addAll(list)
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapUp = { motionEvent, node ->
                    if (node == null) {
                        val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                        hitResults?.firstOrNull {
                            it.isValid(
                                depthPoint = false,
                                point = false
                            )
                        }?.createAnchorOrNull()
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

                    hitResults?.firstOrNull {
                        it.isValid(
                            depthPoint = false,
                            point = false
                        )
                    }?.createAnchorOrNull()
                        ?.let { anchor ->
                            currentAnchor = anchor
                        }
                },
                onScale = { scaleGestureDetector, motionEvent, node ->
                    val scaleFactor = scaleGestureDetector.scaleFactor
                    modelScale *= scaleFactor
                },
                onRotate = { r, m, n ->
                    val rotation = r.currentAngle

                    rotationDegree = when (rotationMode) {
                        RotationAxis.X -> {
                            Rotation(
                                rotationDegree.x * rotation,
                                rotationDegree.y,
                                rotationDegree.z
                            )
                        }

                        RotationAxis.Y -> {
                            Rotation(
                                rotationDegree.x,
                                rotationDegree.y * rotation,
                                rotationDegree.z
                            )
                        }

                        RotationAxis.Z -> {
                            Rotation(
                                rotationDegree.x,
                                rotationDegree.y,
                                rotationDegree.z * rotation
                            )
                        }
                    }
                }
            )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 100.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(onClick = {
                rotationMode = RotationAxis.X
            }) {
                Text("X")
            }
            Button(onClick = {
                rotationMode = RotationAxis.Y
            }) {
                Text("Y")
            }
            Button(onClick = {
                rotationMode = RotationAxis.Z
            }) {
                Text("Z")
            }
        }
    }
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
        scaleToUnits = 0.01f,
        centerOrigin = Position(pose.tx(), pose.qy(), pose.tz())
    ).apply {
        isEditable = true
    }
    anchorNode.addChildNode(modelNode)
    return anchorNode
}