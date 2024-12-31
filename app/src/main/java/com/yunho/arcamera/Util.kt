package com.yunho.arcamera

import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.delay

suspend fun ModelNode.playAnimationOnce(
    emotion: Emotion,
    onAnimationEnd: () -> Unit,
) {
    val duration = animator.getAnimationDuration(emotion.value) * 1000

    playAnimation(emotion.value, 1f, false)

    delay(duration.toLong() + 50)

    onAnimationEnd()
}

suspend fun ModelNode.playAnimationAndResetToIdle(
    emotion: Emotion,
) {
    val duration = animator.getAnimationDuration(emotion.value) * 1000

    playAnimation(emotion.value, 1f, false)

    delay(duration.toLong() + 50)

    playAnimation(0, 1f, true)
}