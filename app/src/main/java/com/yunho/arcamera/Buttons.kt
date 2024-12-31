package com.yunho.arcamera

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
 fun ResetButton(
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
 fun AnimationButton(
    text: String,
    modifier: Modifier = Modifier,
    onAnimate: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = {
                onAnimate()
            },
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "play",
                tint = Color.White
            )
        }

        Text(text, color = Color.White)
    }
}

@Composable
 fun CaptureButton(
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
 fun SaveButton(
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