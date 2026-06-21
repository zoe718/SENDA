package com.voxi.captions.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voxi.captions.model.Utterance
import com.voxi.captions.ui.components.SpeechBubble
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.viewmodel.ConversationUiState

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxiBg)
            .padding(16.dp),
    ) {
        Header(isListening = state.isListening)

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
    }
}

@Composable
private fun Header(isListening: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Voxi",
            style = MaterialTheme.typography.titleLarge,
            color = VoxiTeal,
        )
        Spacer(Modifier.weight(1f))
        if (isListening) ListeningIndicator()
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(pulse)
                .background(VoxiTeal, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Escuchando",
            style = MaterialTheme.typography.labelMedium,
            color = VoxiSlate,
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.utterances, key = Utterance::id) { utterance ->
            SpeechBubble(
                text = utterance.text,
                modifier = Modifier.animateItem(),
            )
        }
        if (state.partialText.isNotEmpty()) {
            item(key = "partial") {
                SpeechBubble(
                    text = state.partialText,
                    isPartial = true,
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
            .graphicsLayer { alpha = 0.9f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = statusMessage ?: "Habla y verás los subtítulos aquí.",
            style = MaterialTheme.typography.bodyLarge,
            color = VoxiSlate,
            textAlign = TextAlign.Center,
        )
    }
}
