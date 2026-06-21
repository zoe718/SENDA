package com.voxi.captions.ui.screens

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurface
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.vision.DetectedFace
import com.voxi.captions.vision.FaceMeshAnalyzer
import com.voxi.captions.viewmodel.ConversationUiState
import java.util.concurrent.Executors

/**
 * Capa 3 Modo B (spec §6): muestra la cámara y ancla el subtítulo junto a la
 * cara que está hablando. Si ML Kit o la cámara fallan, el overlay cae a un
 * subtítulo centrado y la app nunca crashea (spec §10).
 */
@Composable
fun CameraScreen(
    state: ConversationUiState,
    onFacesDetected: (List<DetectedFace>) -> Unit,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, FaceMeshAnalyzer(onFacesDetected)) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(VoxiBg)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        CaptionOverlay(state = state, modifier = Modifier.fillMaxSize())

        TopBar(
            faceCount = state.faces.size,
            onToggleCamera = onToggleCamera,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
        )
    }
}

/** Subtítulo anclado a la X de la cara activa; si no hay cara, va centrado abajo. */
@Composable
private fun CaptionOverlay(
    state: ConversationUiState,
    modifier: Modifier = Modifier,
) {
    val caption = state.partialText.ifBlank { state.utterances.lastOrNull()?.text.orEmpty() }
    if (caption.isBlank()) return

    val active = state.activeFace
    BoxWithConstraints(modifier = modifier) {
        val targetBias = ((active?.cx ?: 0.5f) * 2f - 1f).coerceIn(-1f, 1f)
        val bias by animateFloatAsState(
            targetValue = targetBias,
            animationSpec = tween(250),
            label = "captionBias",
        )
        // Verticalmente: encima de la cara si la hay, si no abajo del todo.
        val verticalBias = active?.let { (it.cy * 2f - 1f).coerceIn(-0.9f, 0.9f) } ?: 0.85f

        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .align(BiasAlignment(bias, verticalBias)),
            contentAlignment = Alignment.Center,
        ) {
            FloatingCaption(text = caption, anchored = active != null)
        }
    }
}

@Composable
private fun FloatingCaption(text: String, anchored: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .background(VoxiSurface.copy(alpha = 0.92f), shape)
            .border(1.dp, VoxiTeal.copy(alpha = 0.85f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (anchored) "Hablante" else "Subtitulo",
            style = MaterialTheme.typography.labelSmall,
            color = VoxiTeal,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoxiMint,
        )
    }
}

@Composable
private fun TopBar(
    faceCount: Int,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(VoxiTeal, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (faceCount > 0) "$faceCount cara(s)" else "Buscando caras…",
                style = MaterialTheme.typography.labelMedium,
                color = VoxiMint,
            )
        }
        BackChip(onClick = onToggleCamera)
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = "Ver chat",
        style = MaterialTheme.typography.labelLarge,
        color = VoxiBg,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(VoxiTeal, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Click simple sin depender de Material ripple, para el chip del overlay. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

/** Alineación con sesgo arbitrario en X/Y (−1..1) para anclar la burbuja. */
private class BiasAlignment(
    private val horizontalBias: Float,
    private val verticalBias: Float,
) : Alignment {
    override fun align(
        size: androidx.compose.ui.unit.IntSize,
        space: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    ): androidx.compose.ui.unit.IntOffset {
        val centerX = (space.width - size.width) / 2f
        val centerY = (space.height - size.height) / 2f
        val x = centerX * (1 + horizontalBias)
        val y = centerY * (1 + verticalBias)
        return androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
    }
}
