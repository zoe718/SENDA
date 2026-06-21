package com.voxi.captions.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.voxi.captions.model.Origin
import com.voxi.captions.model.Utterance
import com.voxi.captions.ui.theme.VoxiTeal
import com.voxi.captions.ui.theme.speakerColor
import com.voxi.captions.viewmodel.SpeakerIdentity

/**
 * Lógica de chat compartida por la conversación en vivo y el historial:
 *  - Lo que se escucha ([Origin.HEARD]) va a la izquierda; lo que dicta el
 *    usuario ([Origin.SELF]) va a la derecha.
 *  - El avatar/nombre del hablante solo aparece cuando cambia respecto a la
 *    frase anterior (mensajes consecutivos del mismo hablante se agrupan).
 */

/** ¿Esta frase abre un grupo nuevo (cambió el hablante o el lado)? */
fun startsNewGroup(previous: Utterance?, current: Utterance): Boolean {
    if (previous == null) return true
    if (previous.origin != current.origin) return true
    return current.origin == Origin.HEARD && previous.speaker != current.speaker
}

/**
 * Coloca la burbuja en su lado como un chat normal: lo escuchado pegado a la
 * IZQUIERDA y lo que dictas tú pegado a la DERECHA. La burbuja envuelve su
 * contenido (no deja hueco) y se limita al 86% del ancho para que las frases
 * largas no toquen el borde opuesto.
 */
@Composable
fun ChatRow(
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.86f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        ) {
            Box(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                content()
            }
        }
    }
}

/**
 * Una burbuja del chat, con lado, color y agrupación ya resueltos.
 *
 * [identity] (spec §6, memoria cara↔voz): si este hablante fue enrolado en el
 * escaneo, trae su nombre real y su foto, que sustituyen a "Hablante N" y al
 * círculo de color por un avatar de verdad.
 */
@Composable
fun ChatBubble(
    utterance: Utterance,
    previous: Utterance?,
    modifier: Modifier = Modifier,
    isPartial: Boolean = false,
    identity: SpeakerIdentity? = null,
) {
    val isSelf = utterance.origin == Origin.SELF
    val showSpeaker = startsNewGroup(previous, utterance)
    val realName = identity?.name?.takeIf { it.isNotBlank() }
    val label = when {
        isSelf -> "Tú"
        realName != null -> realName
        else -> utterance.speaker.displayName
    }
    val color = if (isSelf) VoxiTeal else speakerColor(utterance.speaker)
    ChatRow(alignEnd = isSelf, modifier = modifier) {
        SpeechBubble(
            text = utterance.text,
            tone = utterance.tone,
            isPartial = isPartial,
            speakerName = label,
            speakerColor = color,
            speakerPhoto = if (isSelf) null else identity?.photo,
            alignEnd = isSelf,
            showSpeaker = showSpeaker,
        )
    }
}
