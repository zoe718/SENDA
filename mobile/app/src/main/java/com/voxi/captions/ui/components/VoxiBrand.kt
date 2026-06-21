package com.voxi.captions.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxi.captions.R
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiTeal

/**
 * Marca reutilizable para mantener una identidad consistente en toda la app
 * (spec branding): la insignia usa el logo real de la app (app_logo) y, si se
 * quiere, el nombre con un subtitulo breve.
 */
@Composable
fun VoxiBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = "Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f)),
    )
}

/** Insignia + nombre (+ subtitulo opcional), el lockup de marca del encabezado. */
@Composable
fun VoxiWordmark(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 28.dp,
    subtitle: String? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        VoxiBadge(size = badgeSize)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "SENDA",
                style = MaterialTheme.typography.titleLarge,
                color = VoxiTeal,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxiSlate,
                )
            }
        }
    }
}
