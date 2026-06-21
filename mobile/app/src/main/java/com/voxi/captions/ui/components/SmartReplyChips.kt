package com.voxi.captions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSurface
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal

/**
 * Respuestas sugeridas con IA (estilo "smart replies"): hasta 3 chips neon que
 * la persona sorda puede tocar para que el telefono diga esa frase en voz alta
 * (spec §7). Se generan a partir de lo ultimo que se escucho.
 *
 * Si la lista esta vacia no dibuja nada (no ocupa espacio).
 */
@Composable
fun SmartReplyChips(
    replies: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (replies.isEmpty()) return
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        replies.forEachIndexed { index, reply ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(VoxiSurfaceHigh, VoxiSurface)), shape)
                    .border(1.dp, VoxiTeal.copy(alpha = 0.6f), shape)
                    .clickable { onPick(reply) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text = "✦", style = MaterialTheme.typography.labelMedium, color = VoxiTeal)
                Text(
                    text = reply,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxiMint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
