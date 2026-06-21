package com.voxi.captions.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiBorder
import com.voxi.captions.ui.theme.VoxiBrandGradient
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal

/**
 * Vía de regreso (spec §7): la persona sorda escribe y al enviar el teléfono lo
 * dice en voz alta. Cierra el círculo de la conversación bidireccional.
 */
@Composable
fun ComposeBar(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val hasText = text.trim().isNotEmpty()

    fun send() {
        val message = text.trim()
        if (message.isNotEmpty()) {
            onSend(message)
            text = ""
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .background(VoxiSurfaceHigh, RoundedCornerShape(26.dp))
                .border(1.dp, VoxiBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "Escribe y el teléfono lo dice…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxiSlate,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = VoxiMint),
                cursorBrush = SolidColor(VoxiTeal),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
        }

        Spacer(Modifier.width(10.dp))

        val scale by animateFloatAsState(
            targetValue = if (hasText) 1f else 0.9f,
            animationSpec = tween(180),
            label = "sendScale",
        )
        Box(
            modifier = Modifier
                .scale(scale)
                .size(52.dp)
                .shadow(
                    elevation = if (hasText) 16.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = VoxiTeal,
                    spotColor = VoxiTeal,
                )
                .background(
                    if (hasText) VoxiBrandGradient else SolidColor(VoxiSurfaceHigh),
                    CircleShape,
                )
                .clickable(enabled = hasText) { send() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "↑",
                style = MaterialTheme.typography.titleLarge,
                color = if (hasText) VoxiBg else VoxiSlate,
            )
        }
    }
}
