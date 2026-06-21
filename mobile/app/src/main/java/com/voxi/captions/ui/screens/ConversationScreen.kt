package com.voxi.captions.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voxi.captions.model.Origin
import com.voxi.captions.model.Speaker
import com.voxi.captions.model.Utterance
import com.voxi.captions.ui.components.ChatBubble
import com.voxi.captions.ui.components.ComposeBar
import com.voxi.captions.ui.components.SmartReplyChips
import com.voxi.captions.ui.components.VoxiBadge
import com.voxi.captions.ui.components.VoxiWordmark
import com.voxi.captions.ui.theme.VoxiAmber
import com.voxi.captions.ui.theme.VoxiAmbientGlow
import com.voxi.captions.ui.theme.VoxiBackground
import com.voxi.captions.ui.theme.VoxiBorder
import com.voxi.captions.ui.theme.VoxiMint
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiSurfaceHigh
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.ui.theme.speakerColor
import com.voxi.captions.viewmodel.ConversationUiState

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    modifier: Modifier = Modifier,
    onSelectSpeaker: (Speaker?) -> Unit = {},
    onSend: (String) -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onExport: () -> Unit = {},
    onHistory: () -> Unit = {},
    onNewConversation: () -> Unit = {},
    onChangeVoice: () -> Unit = {},
    onToggleMute: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoxiBackground),
    ) {
        // Halo neon ambiental detras del encabezado para dar profundidad premium.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(VoxiAmbientGlow),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // safeDrawing incluye barras del sistema + IME: la barra de escritura
                // se eleva con el teclado y nada se rompe (fix responsive).
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Header(isListening = state.isListening, isMuted = state.isMuted)

            Spacer(Modifier.size(14.dp))

            ActionsRow(
                canExport = state.utterances.isNotEmpty(),
                isMuted = state.isMuted,
                onNewConversation = onNewConversation,
                onHistory = onHistory,
                onExport = onExport,
                onToggleCamera = onToggleCamera,
                onChangeVoice = onChangeVoice,
                onToggleMute = onToggleMute,
            )

            Spacer(Modifier.size(12.dp))

            SpeakerSelector(
                manualSpeaker = state.manualSpeaker,
                speakers = state.knownSpeakers,
                onSelect = onSelectSpeaker,
            )

            Spacer(Modifier.size(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isModelLoading -> LoadingState()
                    state.utterances.isEmpty() && state.partialText.isEmpty() ->
                        EmptyState(state.statusMessage)
                    else -> Conversation(state)
                }
            }

            state.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = VoxiSlate,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.suggestedReplies.isNotEmpty()) {
                Spacer(Modifier.size(10.dp))
                SmartReplyChips(replies = state.suggestedReplies, onPick = onSend)
            }

            Spacer(Modifier.size(10.dp))

            ComposeBar(onSend = onSend)
        }
    }
}

@Composable
private fun Header(isListening: Boolean, isMuted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VoxiWordmark(subtitle = "Subtitulos vivos", badgeSize = 34.dp)
        Spacer(Modifier.weight(1f))
        when {
            isMuted -> MutedIndicator()
            isListening -> ListeningIndicator()
        }
    }
}

/** Acciones del chat en una fila desplazable para que nunca se desborde. */
@Composable
private fun ActionsRow(
    canExport: Boolean,
    isMuted: Boolean,
    onNewConversation: () -> Unit,
    onHistory: () -> Unit,
    onExport: () -> Unit,
    onToggleCamera: () -> Unit,
    onChangeVoice: () -> Unit,
    onToggleMute: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillButton(label = "Nueva", onClick = onNewConversation, primary = true)
        // Capa 4: silenciar/reanudar el microfono. Resalta su estado activo.
        PillButton(
            label = if (isMuted) "Reanudar" else "Silenciar",
            onClick = onToggleMute,
            active = isMuted,
        )
        PillButton(label = "Camara", onClick = onToggleCamera)
        PillButton(label = "Voz", onClick = onChangeVoice)
        PillButton(label = "Historial", onClick = onHistory)
        if (canExport) PillButton(label = "Exportar", onClick = onExport)
    }
}

/** Boton tipo pildora glassy, para acciones del header. */
@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    active: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val accent = when {
        active -> VoxiAmber
        primary -> VoxiTeal
        else -> VoxiSlate
    }
    val content = when {
        active -> VoxiAmber
        primary -> VoxiTeal
        else -> VoxiMint
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        modifier = Modifier
            .clip(shape)
            .background(if (active) VoxiAmber.copy(alpha = 0.16f) else VoxiSurfaceHigh)
            .border(1.dp, accent.copy(alpha = if (primary || active) 0.55f else 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Indicador de microfono silenciado (Capa 4), en ambar para diferenciarlo. */
@Composable
private fun MutedIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(VoxiSurfaceHigh)
            .border(1.dp, VoxiAmber.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(VoxiAmber, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Silenciado",
            style = MaterialTheme.typography.labelMedium,
            color = VoxiAmber,
        )
    }
}

/**
 * Selector de hablante (spec §6, Modo A): "Auto" usa la diarización por huella
 * de voz; los demás chips fijan manualmente a una de las voces ya descubiertas.
 * La fila es desplazable porque pueden aparecer hasta 8 hablantes.
 */
@Composable
private fun SpeakerSelector(
    manualSpeaker: Speaker?,
    speakers: List<Speaker>,
    onSelect: (Speaker?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = manualSpeaker == null,
            onClick = { onSelect(null) },
            label = { Text("Auto") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VoxiTeal.copy(alpha = 0.22f),
                selectedLabelColor = VoxiTeal,
            ),
        )
        speakers.forEach { speaker ->
            val color = speakerColor(speaker)
            FilterChip(
                selected = manualSpeaker == speaker,
                onClick = { onSelect(speaker) },
                label = { Text(speaker.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.25f),
                    selectedLabelColor = color,
                ),
            )
        }
    }
}

@Composable
private fun ListeningIndicator() {
    val transition = rememberInfiniteTransition(label = "listening")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(VoxiSurfaceHigh)
            .border(1.dp, VoxiTeal.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .alpha(pulse)
                .background(VoxiTeal, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Escuchando",
            style = MaterialTheme.typography.labelMedium,
            color = VoxiMint,
        )
    }
}

@Composable
private fun Conversation(state: ConversationUiState) {
    val listState = rememberLazyListState()

    // Auto-scroll al final cuando llega texto nuevo.
    LaunchedEffect(state.utterances.size, state.partialText) {
        val total = state.utterances.size + if (state.partialText.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.utterances, key = { _, u -> u.id }) { index, utterance ->
            ChatBubble(
                utterance = utterance,
                previous = state.utterances.getOrNull(index - 1),
                identity = state.speakerIdentities[utterance.speaker.index],
                modifier = Modifier.animateItem(),
            )
        }
        if (state.partialText.isNotEmpty()) {
            item(key = "partial") {
                val partial = Utterance(
                    id = -1L,
                    text = state.partialText,
                    tone = state.partialTone,
                    speaker = state.partialSpeaker,
                    origin = Origin.HEARD,
                )
                ChatBubble(
                    utterance = partial,
                    previous = state.utterances.lastOrNull(),
                    isPartial = true,
                    identity = state.speakerIdentities[state.partialSpeaker.index],
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = VoxiTeal)
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Preparando el modelo de voz…",
            style = MaterialTheme.typography.bodyLarge,
            color = VoxiSlate,
        )
    }
}

@Composable
private fun EmptyState(statusMessage: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.95f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoxiBadge(size = 64.dp)
        Spacer(Modifier.size(20.dp))
        Text(
            text = "Todo listo para escuchar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoxiMint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = statusMessage ?: "Habla y veras los subtitulos aparecer aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = VoxiSlate,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
