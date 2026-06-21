package com.voxi.captions.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Acentos (paleta Voxi) ---
val VoxiTeal = Color(0xFF3DF2C0)   // primario / activo / energia (neon)
val VoxiBlue = Color(0xFF57B7FF)   // secundario / hablante 2 / calma
val VoxiViolet = Color(0xFF9B84D2) // enfasis / glow
val VoxiMint = Color(0xFFE8FBF4)   // texto alto contraste casi blanco
val VoxiSlate = Color(0xFF7C8AA3)  // texto secundario / bordes / atenuado
val VoxiAmber = Color(0xFFFFB454)  // estado silenciado / advertencia (Capa 4)

// Acento neon extra para halos y estados activos (mas saturado que el teal base).
val VoxiNeon = Color(0xFF2AF5C8)
val VoxiNeonViolet = Color(0xFF7C5CFF) // segundo neon para degradados de marca

// --- Paleta de hablantes (spec §6/§8) ---
// Un color distinto por cada voz detectada por la diarizacion. El teal se
// reserva para "Tu" (mensajes propios por TTS), asi que aqui no se incluye.
// Con mas de 8 hablantes los colores se reciclan (index % size).
val VoxiSpeakerPalette = listOf(
    Color(0xFF57B7FF), // 1 azul
    Color(0xFFB68CFF), // 2 violeta
    Color(0xFFFFB454), // 3 ambar
    Color(0xFFFF7A8A), // 4 coral
    Color(0xFF8FD14F), // 5 lima
    Color(0xFFE08CFF), // 6 magenta
    Color(0xFF4DD0E1), // 7 cian
    Color(0xFFFF6E6E), // 8 rojo
)

// --- Neutros oscuros (mas profundos, con tinte frio para look premium) ---
val VoxiBg = Color(0xFF05070C)         // fondo base casi negro
val VoxiBgElevated = Color(0xFF0A0F18) // fondo con un punto mas de luz (gradiente)
val VoxiSurface = Color(0xFF10161F)    // tarjetas / barras
val VoxiSurfaceHigh = Color(0xFF18202C) // superficie elevada / campos
val VoxiSurfaceGlass = Color(0xFF1C2530) // superficie tipo vidrio (con alpha en uso)
val VoxiBorder = Color(0xFF263247)     // bordes sutiles sobre superficies

// --- Gradientes / brushes reutilizables (spec §8: que se vea premium) ---

/**
 * Fondo principal: degradado vertical profundo con un leve halo neon arriba para
 * dar sensacion de profundidad (estilo oscuro premium tipo Spotify/Arc).
 */
val VoxiBackground: Brush
    get() = Brush.verticalGradient(
        0f to Color(0xFF0B1622),
        0.28f to VoxiBgElevated,
        1f to VoxiBg,
    )

/** Halo radial neon suave para colocar detras del header / indicadores. */
val VoxiAmbientGlow: Brush
    get() = Brush.radialGradient(
        colors = listOf(VoxiTeal.copy(alpha = 0.16f), Color.Transparent),
        center = Offset(0.5f, 0f),
        radius = 900f,
    )

/** Degradado de marca para el logo y los botones de accion principales. */
val VoxiBrandGradient: Brush
    get() = Brush.linearGradient(listOf(VoxiTeal, VoxiNeonViolet))

/** Relleno de tarjeta/burbuja con profundidad (claro arriba, oscuro abajo). */
val VoxiCardGradient: Brush
    get() = Brush.verticalGradient(listOf(VoxiSurfaceHigh, VoxiSurface))

/** Degradado vertical tenue del color de un hablante (para burbujas/acentos). */
fun speakerGradient(base: Color): Brush =
    Brush.linearGradient(listOf(base.copy(alpha = 0.22f), base.copy(alpha = 0.04f)))

/** Halo neon del color dado, para glows detras de avatares/botones activos. */
fun glowGradient(base: Color): Brush =
    Brush.radialGradient(listOf(base.copy(alpha = 0.55f), Color.Transparent))
