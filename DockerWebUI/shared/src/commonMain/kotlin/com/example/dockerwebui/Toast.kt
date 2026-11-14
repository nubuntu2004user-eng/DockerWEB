package com.example.dockerwebui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class ToastState {
    var isVisible by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var icon by mutableStateOf<ImageVector?>(null)
        private set

    suspend fun showToast(
        message: String,
        icon: ImageVector? = null,
        durationMillis: Long = 2000L
    ) {
        this.message = message
        this.icon = icon
        this.isVisible = true
        delay(durationMillis)
        this.isVisible = false
    }
}

@Composable
fun ToastHost(
    hostState: ToastState,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter
) {
    Box(
        modifier = modifier,
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = hostState.isVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .background(Color(31, 111, 235), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                hostState.icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 8.dp).size(20.dp)
                    )
                }
                Text(
                    text = hostState.message,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}
