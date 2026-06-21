package com.voxi.captions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurface
import com.voxi.captions.ui.theme.VoxiTeal

/**
 * Burbuja de chat para una intervención (spec §8).
 *
 * En la Capa 1 el estilo es fijo. La Capa 2 hará variar tamaño/peso/color según el tono.
 */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    isPartial: Boolean = false,
    accent: Color = VoxiTeal,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isPartial) VoxiSlate else MaterialTheme.colorScheme.onSurface,
        fontStyle = if (isPartial) FontStyle.Italic else FontStyle.Normal,
        modifier = modifier
            .background(VoxiSurface, RoundedCornerShape(20.dp))
            .border(1.dp, accent.copy(alpha = if (isPartial) 0.3f else 0.8f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
