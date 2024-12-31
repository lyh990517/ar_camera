package com.yunho.arcamera

import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.delay

suspend fun ModelNode.playAnimationOnce(
    animationIndex: Int,
    onAnimationEnd: () -> Unit,
) {
    val animationName = animator.getAnimationName(animationIndex)
    val duration = animator.getAnimationDuration(animationIndex) * 1000
    playAnimation(animationName, 1f, false)

    delay(duration.toLong())

    onAnimationEnd()
}
