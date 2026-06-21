package com.voxi.captions.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiSlate
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

    fun send() {
        val message = text.trim()
        if (message.isNotEmpty()) {
            onSend(message)
            text = ""
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            placeholder = {
                Text("Escribe y el teléfono lo dice…", color = VoxiSlate)
            },
            shape = RoundedCornerShape(16.dp),
            maxLines = 3,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Send,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = { send() },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VoxiTeal,
                unfocusedBorderColor = VoxiSlate,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Button(
            onClick = { send() },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoxiTeal,
                contentColor = VoxiBg,
            ),
        ) {
            Text("Decir")
        }
    }
}
