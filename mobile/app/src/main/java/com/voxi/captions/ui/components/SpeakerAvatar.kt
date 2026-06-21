package com.voxi.captions.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.voxi.captions.ui.theme.VoxiBg

/**
 * Avatar circular del hablante con anillo de brillo del color de su carril. Si
 * hay [photo] (rostro capturado en el escaneo, spec §6) se muestra la foto;
 * si no, la inicial/numero sobre un degradado del color. Da identidad visual a
 * cada burbuja (spec §8).
 */
@Composable
fun SpeakerAvatar(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 36,
    dimmed: Boolean = false,
    photo: ImageBitmap? = null,
) {
    val alpha = if (dimmed) 0.5f else 1f
    // Anillo exterior (glow) + nucleo. El anillo da el efecto neon.
    Box(
        modifier = modifier
            .size((size + 4).dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.45f * alpha), Color.Transparent),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.6f)),
                    ),
                )
                .border(1.5.dp, color.copy(alpha = 0.9f * alpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(1.5.dp)
                        .size(size.dp)
                        .clip(CircleShape),
                )
            } else {
                Text(
                    text = initialOf(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = VoxiBg,
                )
            }
        }
    }
}

/** Prefiere la inicial de un nombre real; si es "Hablante N" usa el numero. */
private fun initialOf(label: String): String {
    val trimmed = label.trim()
    if (trimmed.startsWith("Hablante", ignoreCase = true)) {
        trimmed.firstOrNull { it.isDigit() }?.let { return it.toString() }
    }
    return (trimmed.firstOrNull() ?: '?').toString().uppercase()
}
