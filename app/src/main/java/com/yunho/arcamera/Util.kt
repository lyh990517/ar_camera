package com.yunho.arcamera

import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.delay

suspend fun ModelNode.playAnimationOnce(
    index: Int,
    onAnimationEnd: () -> Unit,
) {
    val duration = animator.getAnimationDuration(index) * 1000

    playAnimation(index, 1f, false)

    delay(duration.toLong() + 50)

    onAnimationEnd()
}
