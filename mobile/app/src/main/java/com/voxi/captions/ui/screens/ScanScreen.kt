package com.voxi.captions.ui.screens

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.voxi.captions.ui.components.VoxiBadge
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurface
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.vision.DetectedFace
import com.voxi.captions.vision.FaceAnalyzer
import com.voxi.captions.viewmodel.ScanPerson
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Capa 2 (spec §6): pantalla de arranque "camara-primero". Antes de abrir la
 * conversacion, Voxi muestra el video en vivo y dibuja un marco sobre CADA
 * rostro presente. El usuario TOCA el marco de quien quiere recordar: ahi se
 * captura su miniatura nitida y se enrola (rostro + nombre opcional + foto).
 *
 * Control total: puede agregar a varias personas, quitar a las que sobren (✕),
 * ponerles nombre y luego empezar. Tambien puede omitir; si la camara falla,
 * nunca crashea (§10).
 */
@Composable
fun ScanScreen(
    people: List<ScanPerson>,
    liveFaces: List<DetectedFace>,
    onScanFaces: (List<DetectedFace>) -> Unit,
    onCaptureFace: (Int) -> Unit,
    onRemovePerson: (Int) -> Unit,
    onName: (Int, String) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onReRecordVoice: (Int) -> Unit = {},
    scanRecordingLocalId: Int = -1,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    // Persona que se esta nombrando (dialog abierto), o null.
    var naming by remember { mutableStateOf<ScanPerson?>(null) }

    // trackIds ya enrolados, para marcar su rostro como "ya agregado" en vivo.
    val enrolledTracks = remember(people) {
        people.mapNotNull { it.trackId.takeIf { id -> id >= 0 } }.toHashSet()
    }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val previewSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder()
                    .setResolutionSelector(previewSelector)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                // En escaneo pedimos mayor resolucion: fotos de rostro mas nitidas.
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    // captureThumbnails = true: en escaneo si recortamos la foto.
                    .also {
                        it.setAnalyzer(
                            analysisExecutor,
                            FaceAnalyzer(onScanFaces, captureThumbnails = { true }),
                        )
                    }

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
        // Preview + overlay de marcos tocables, mapeados al recorte FILL_CENTER.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            val density = LocalDensity.current
            val viewW = with(density) { maxWidth.toPx() }
            val viewH = with(density) { maxHeight.toPx() }

            liveFaces.forEach { face ->
                FaceMarker(
                    face = face,
                    viewW = viewW,
                    viewH = viewH,
                    enrolled = face.trackId >= 0 && face.trackId in enrolledTracks,
                    onTap = { if (face.trackId >= 0) onCaptureFace(face.trackId) },
                )
            }
        }

        // Encabezado: marca + instruccion.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoxiBadge(size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Conoce a quienes te acompanan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoxiMint,
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = if (liveFaces.isEmpty()) {
                    "Apunta la camara hacia las personas"
                } else {
                    "Toca el rostro de quien quieras recordar"
                },
                style = MaterialTheme.typography.bodySmall,
                color = VoxiSlate,
                textAlign = TextAlign.Center,
            )
        }

        // Pie: rostros capturados + acciones.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            if (people.isNotEmpty()) {
                Text(
                    text = "${people.size} agregada(s) - toca para nombrar",
                    style = MaterialTheme.typography.labelMedium,
                    color = VoxiTeal,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    people.forEach { person ->
                        ScanFaceCard(
                            person = person,
                            onClick = { naming = person },
                            onRemove = { onRemovePerson(person.localId) },
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScanButton(
                    label = "Omitir",
                    onClick = onSkip,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
                ScanButton(
                    label = if (people.isEmpty()) "Empezar" else "Empezar (${people.size})",
                    onClick = onFinish,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Grabando la voz de la persona recien tocada: frase + progreso de ~6 s.
        if (scanRecordingLocalId >= 0) {
            VoiceEnrollOverlay()
        }
    }

    naming?.let { person ->
        NameDialog(
            person = person,
            onConfirm = { name ->
                onName(person.localId, name)
                naming = null
            },
            onReRecord = {
                naming = null
                onReRecordVoice(person.localId)
            },
            onDismiss = { naming = null },
        )
    }
}

/** Frase sugerida para el enrolamiento de voz (suficiente para ~6 s de audio). */
private const val VOICE_PHRASE =
    "Hola, que gusto verte. Asi suena mi voz cuando hablo. " +
        "Me alegra mucho poder conversar contigo y que entiendas lo que digo."

/**
 * Capa de grabacion de voz (spec 6): cubre la pantalla mientras se capturan ~6 s
 * de la persona. Muestra una frase sugerida (la voz es text-independent, asi que
 * el contenido no importa: solo asegura que hable lo suficiente) y un progreso.
 */
@Composable
private fun VoiceEnrollOverlay() {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 6000, easing = LinearEasing),
        label = "voiceEnroll",
    )
    LaunchedEffect(Unit) { started = true }
    Box(
        modifier = Modifier.fillMaxSize().background(VoxiBg.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(VoxiSurface)
                .border(1.dp, VoxiTeal.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Escuchando la voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VoxiTeal,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Pidele que diga en voz alta:",
                style = MaterialTheme.typography.bodySmall,
                color = VoxiSlate,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "\"$VOICE_PHRASE\"",
                style = MaterialTheme.typography.titleSmall,
                color = VoxiMint,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(VoxiSurfaceHigh),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(VoxiTeal),
                )
            }
        }
    }
}

/**
 * Dibuja un marco neon sobre el rostro [face], mapeando sus coordenadas
 * normalizadas al recorte FILL_CENTER del preview (igual que la camara en vivo).
 * Verde brillante = se puede tocar para agregar; tenue con ✓ = ya agregado.
 */
@Composable
private fun FaceMarker(
    face: DetectedFace,
    viewW: Float,
    viewH: Float,
    enrolled: Boolean,
    onTap: () -> Unit,
) {
    if (viewW <= 0f || viewH <= 0f || face.imageAspect <= 0f) return
    val density = LocalDensity.current

    // FILL_CENTER: escala la imagen para LLENAR la vista y recorta el sobrante.
    val imgW = face.imageAspect
    val imgH = 1f
    val scale = max(viewW / imgW, viewH / imgH)
    val scaledW = imgW * scale
    val scaledH = imgH * scale
    val offX = (viewW - scaledW) / 2f
    val offY = (viewH - scaledH) / 2f

    val boxW = face.widthRatio * scaledW
    val boxH = face.heightRatio * scaledH
    val left = offX + face.cx * scaledW - boxW / 2f
    val top = offY + face.cy * scaledH - boxH / 2f

    // Fuera de pantalla (recortado): no dibujar.
    if (left + boxW < 0f || top + boxH < 0f || left > viewW || top > viewH) return

    val shape = RoundedCornerShape(14.dp)
    val accent = if (enrolled) VoxiSlate else VoxiTeal
    val wDp = with(density) { boxW.toDp() }
    val hDp = with(density) { boxH.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(width = wDp, height = hDp)
            .clip(shape)
            .background(
                if (enrolled) VoxiTeal.copy(alpha = 0.10f) else VoxiTeal.copy(alpha = 0.06f),
            )
            .border(if (enrolled) 2.dp else 3.dp, accent, shape)
            .clickable(enabled = !enrolled, onClick = onTap),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (enrolled) {
            Text(
                text = "OK",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = VoxiBg,
                modifier = Modifier
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(VoxiTeal)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        } else {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VoxiBg,
                modifier = Modifier
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(VoxiTeal),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScanFaceCard(person: ScanPerson, onClick: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val named = !person.name.isNullOrBlank()
    val accent = if (named) VoxiTeal else VoxiSlate
    Box {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(VoxiSurface.copy(alpha = 0.92f))
                .border(1.dp, accent.copy(alpha = 0.6f), shape)
                .clickable(onClick = onClick)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = person.photo,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, accent.copy(alpha = 0.85f), CircleShape),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = person.name?.takeIf { it.isNotBlank() } ?: "Toca para nombrar",
                style = MaterialTheme.typography.labelSmall,
                color = if (named) VoxiMint else VoxiSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.Center,
            )
        }
        // Sello "voz lista": la persona ya tiene su huella de voz grabada.
        if (person.hasVoice) {
            Text(
                text = "voz",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = VoxiBg,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(VoxiTeal)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        // Boton quitar (✕) en la esquina superior derecha de la tarjeta.
        Text(
            text = "x",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = VoxiMint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(VoxiBg.copy(alpha = 0.85f))
                .border(1.dp, VoxiSlate.copy(alpha = 0.7f), CircleShape)
                .clickable(onClick = onRemove)
                .padding(top = 1.dp),
        )
    }
}

@Composable
private fun ScanButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val container = if (primary) VoxiTeal else VoxiSurfaceHigh
    val content = if (primary) VoxiBg else VoxiMint
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = content,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .background(container)
            .border(1.dp, (if (primary) VoxiTeal else VoxiSlate).copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun NameDialog(
    person: ScanPerson,
    onConfirm: (String) -> Unit,
    onReRecord: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(person.localId) { mutableStateOf(person.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoxiSurface,
        titleContentColor = VoxiMint,
        textContentColor = VoxiSlate,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Guardar", color = VoxiTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = VoxiSlate)
            }
        },
        title = { Text("Nombre de la persona") },
        text = {
            Column {
                Image(
                    bitmap = person.photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(2.dp, VoxiTeal.copy(alpha = 0.8f), CircleShape),
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Ej. Ana", color = VoxiSlate) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VoxiMint,
                        unfocusedTextColor = VoxiMint,
                        focusedBorderColor = VoxiTeal,
                        unfocusedBorderColor = VoxiSlate.copy(alpha = 0.5f),
                        cursorColor = VoxiTeal,
                    ),
                )
                Spacer(Modifier.size(6.dp))
                TextButton(onClick = onReRecord) {
                    Text(
                        text = if (person.hasVoice) "Regrabar voz" else "Grabar voz",
                        color = VoxiTeal,
                    )
                }
            }
        },
    )
}
