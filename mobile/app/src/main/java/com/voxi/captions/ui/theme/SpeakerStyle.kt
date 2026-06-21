package com.voxi.captions.ui.theme

import androidx.compose.ui.graphics.Color
import com.voxi.captions.model.Speaker

/** Color base por hablante (spec §8): cada carril tiene su acento. */
fun speakerColor(speaker: Speaker): Color = when (speaker) {
    Speaker.ONE -> VoxiTeal
    Speaker.TWO -> VoxiBlue
}
