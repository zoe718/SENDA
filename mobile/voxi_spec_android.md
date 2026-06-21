# Voxi — Especificación técnica para Android Studio

> App de **subtítulos espaciales con tono de voz** para personas sordas y con baja audición.
> La conversación se vuelve un chat vivo: cada persona aparece **ubicada en el espacio** y su
> texto **cambia según cómo lo dijo** (volumen, entonación, emoción). Lo nuevo no es subtitular
> —eso ya existe— sino **unir espacio + tono en una sola interfaz**, que hoy no tiene ningún producto.

**Nombre:** Voxi — de *vox* (voz en latín): la voz hecha visible, con su tono y su lugar en el espacio.

---

## 0. Lectura honesta del alcance (léela primero)

Hay tres capas, ordenadas por confiabilidad. Construye de abajo hacia arriba. **La demo debe verse increíble aunque solo llegues a la Capa 2.**

| Capa | Qué hace | Confiabilidad | Prioridad |
|------|----------|---------------|-----------|
| 1. Núcleo | Subtítulos en vivo en español, continuos, offline | Muy alta | Imprescindible |
| 2. Tono | El texto cambia de tamaño/peso/color/animación según prosodia | Alta | Imprescindible |
| 3. Espacio | Ubicar al hablante (cámara + labios, o carriles izq/der, o tocar cara) | Media (con plan B) | El diferenciador |

**Regla de oro:** nada de la Capa 3 debe poder tumbar la app. Si la cámara o la detección de hablante fallan, Voxi cae automáticamente a "un solo carril" y sigue subtitulando. Ver §10.

---

## 1. Stack técnico (decidido, no opcional)

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose (Material 3) + animaciones Compose
- **Arquitectura:** MVVM con `ViewModel` + `StateFlow` + Coroutines
- **Audio crudo:** `AudioRecord` (16 kHz, mono, PCM 16-bit) — necesario para medir prosodia
- **Reconocimiento de voz (ASR):** **Vosk** offline (`vosk-android`), modelo `vosk-model-small-es-0.42` empacado en `assets/` → funciona **sin internet**, continuo, en español
- **Cámara:** CameraX (`ImageAnalysis` para análisis en vivo, `Preview` para mostrar)
- **Caras y boca:** **ML Kit Face Mesh Detection** (on-device) para landmarks de boca → detectar quién mueve los labios
- **Texto a voz (vía de regreso):** `TextToSpeech` nativo de Android (español, offline). ElevenLabs **opcional** si hay internet (ver §5.4)
- **minSdk 26, targetSdk 34** (ajusta a la versión actual de tu Android Studio)

> ¿Por qué Vosk y no `SpeechRecognizer` de Android? Porque `SpeechRecognizer` está pensado para frases cortas y se corta en cada pausa; forzar continuidad genera bugs. Vosk transcribe de forma continua, offline y trae modelo de español. Es la opción que **funciona el día del evento aunque no haya wifi**.

---

## 2. Arquitectura y flujo de datos

**Un solo micrófono, dos consumidores.** No abras dos `AudioRecord` (Android no lo permite bien). Abre uno y reparte cada buffer:

```
AudioRecord (16kHz mono PCM16)
        │  (un hilo IO leyendo buffers de ~20-30 ms)
        ├──► Vosk Recognizer.acceptWaveform(buffer)  ──► texto parcial/final (Flow)
        └──► ProsodyAnalyzer.feed(buffer)            ──► {volumen, pitch, ritmo} (Flow)

CameraX ImageAnalysis ──► ML Kit FaceMesh ──► [carasVisibles + bocaAbierta%] (Flow)

ViewModel: fusiona los tres Flows ──► UiState (lista de "burbujas" de conversación) ──► Compose
```

**Fusión de hablante activo (la lógica clave):**
```
si energíaAudio > umbral Y existe una cara con bocaAbierta% alto y sostenido:
    hablante = esa cara (usa su posición X en pantalla)
si no hay cámara o es ambiguo:
    hablante = carril activo manual (plan B) o "Hablante único"
```

**Hilos:**
- Audio: `Dispatchers.IO`, un loop dedicado.
- ML Kit: executor propio de CameraX.
- UI: `Dispatchers.Main`, todo vía `StateFlow`.
Nunca bloquees el hilo principal con DSP ni con ML Kit.

---

## 3. Estructura de paquetes

```
com.voxi.captions/
├── MainActivity.kt
├── VoxiApp.kt
├── ui/
│   ├── theme/ (Color.kt, Type.kt, Theme.kt)   ← paleta exacta aquí
│   ├── screens/ConversationScreen.kt          ← chat espacial (modo principal)
│   ├── screens/CameraScreen.kt                ← modo AR con caras (Capa 3)
│   ├── components/SpeechBubble.kt             ← burbuja con estilo por tono
│   ├── components/SpeakerLane.kt
│   └── components/ComposeBar.kt               ← escribir + TTS (vía de regreso)
├── audio/
│   ├── AudioCapture.kt                        ← AudioRecord + reparto de buffers
│   ├── ProsodyAnalyzer.kt                     ← volumen (RMS), pitch (YIN), ritmo
│   └── VoskEngine.kt                          ← carga modelo, acceptWaveform
├── vision/
│   ├── FaceMeshAnalyzer.kt                    ← ML Kit, boca abierta %, posición
│   └── ActiveSpeaker.kt                       ← fusión audio+labios
├── tts/
│   ├── AndroidTts.kt
│   └── ElevenLabsTts.kt                       ← opcional, con proxy/clave en BuildConfig
├── model/  (Speaker.kt, Utterance.kt, Tone.kt)
└── viewmodel/ConversationViewModel.kt
```

---

## 4. Capa 1 — Subtítulos en vivo (Vosk)

**Requisitos:**
- Cargar `vosk-model-small-es-0.42` desde `assets/` la primera vez (copiar a almacenamiento interno).
- Mostrar **resultado parcial** mientras la persona habla (gris/atenuado) y **fijarlo** cuando Vosk da el resultado final (color pleno). Esto da sensación de "en vivo".
- Reiniciar limpio si el reconocedor se atasca (watchdog: si no hay parciales en 8 s y hay energía de audio, reinicia el Recognizer).

**Listo cuando:** hablas en español frente al teléfono y el texto aparece de corrido, sin cortarse en cada pausa, con latencia menor a ~1 s.

---

## 5. Capa 2 — Tono de voz hecho visible (tu pieza segura y vistosa)

`ProsodyAnalyzer` calcula 3 señales por cada ventana de ~30 ms y las suaviza (EMA):

- **Volumen** = RMS del buffer (qué tan fuerte habla).
- **Pitch** = frecuencia fundamental con **YIN** o autocorrelación (qué tan agudo/grave).
- **Ritmo** = picos de energía por segundo (qué tan rápido).

**Mapeo a estilo visual (defínelo como constantes ajustables arriba del archivo):**

| Señal de voz | Estilo en pantalla |
|---|---|
| Volumen alto (grita) | Texto más grande + peso bold + leve "shake" |
| Volumen bajo (susurra) | Texto pequeño, atenuado, itálica |
| Pitch alto + ritmo rápido | Acento **`#42E8B4`** (energía/alegría) |
| Pitch bajo + ritmo lento | Acento **`#57B7FF`** o **`#64748B`** (calma/seriedad) |
| Pitch que sube al final | Agrega "?" y un destello |
| Pico súbito de volumen | Burbuja con glow **`#9B84D2`** (énfasis/alarma) |

> Importante: esto NO es lo que vendes como "lo nuevo" (Google ya tiene *Expressive Captions*). Es tu pieza confiable y bonita. Lo nuevo es unirlo con el espacio (§6).

**Stretch opcional:** un modelo TFLite de emoción de voz (feliz/enojado/neutro/triste) que refuerce el color. Solo si te sobra tiempo.

**Listo cuando:** al gritar el texto crece y al susurrar se encoge, en tiempo real, sin trabar la UI.

---

## 6. Capa 3 — Diferenciación espacial de hablantes (el diferenciador)

Tres modos, del más confiable al más impresionante. **Implementa el modo A sí o sí; los otros si hay tiempo.**

**Modo A — Carriles + asignar (plan B, infalible):**
La pantalla tiene carriles (ej. izquierda / derecha / centro). El usuario **toca un carril** para indicar quién habla, o la app usa diarización simple por cambios de pitch para alternar "Hablante 1 / 2". Cero dependencia de cámara. Siempre funciona.

**Modo B — Cámara + labios (lo que sorprende):**
- CameraX muestra el mundo; ML Kit FaceMesh detecta caras y el **% de apertura de boca** (distancia entre landmarks de labio superior e inferior, normalizada).
- Cuando hay energía de audio Y una cara mantiene la boca moviéndose, esa cara = hablante activo.
- La burbuja del subtítulo se **ancla a la posición X de esa cara** y la sigue.
- Funciona bien con **2 personas**; no prometas 6. Si hay ambigüedad, cae a Modo A.

**Modo C — Izquierda/derecha por estéreo (extra):**
Si el dispositivo da audio estéreo, compara energía L vs R para colocar el subtítulo a un lado. Aproximado; úsalo solo como refuerzo.

**Listo cuando:** dos personas hablan por turnos y cada frase aparece del lado/junto a quien la dijo, y si tapas la cámara, sigue funcionando en Modo A sin crashear.

---

## 7. Vía de regreso (conversación bidireccional)

`ComposeBar`: la persona sorda escribe y al enviar:
- **Por defecto:** `TextToSpeech` nativo en español (offline, confiable) lo dice en voz alta.
- **Opcional (si hay internet):** ElevenLabs para una voz humana expresiva. Clave en `BuildConfig`/local.properties, **nunca hardcodeada en el repo**; idealmente detrás de un proxy. Si falla la red, cae a TTS nativo automáticamente.

Esto cierra el círculo: oyente habla → subtítulos; persona sorda escribe → voz.

---

## 8. Diseño de UI (que se vea increíble)

**Tema oscuro** (un captioner se lee mejor sobre fondo oscuro y luce premium). Tu paleta como acentos sobre neutros oscuros.

**Tokens de color (`ui/theme/Color.kt`):**
```kotlin
// Acentos (tus colores)
val VoxiTeal    = Color(0xFF42E8B4)  // primario / activo / energía
val VoxiBlue    = Color(0xFF57B7FF)  // secundario / hablante 2 / calma
val VoxiViolet  = Color(0xFF9B84D2)  // ⚠️ provisional: tu #HJ84D2 es inválido. Reemplázalo.
val VoxiMint    = Color(0xFFC2EDE0)  // texto alto contraste / superficies suaves
val VoxiSlate   = Color(0xFF64748B)  // texto secundario / bordes / atenuado

// Neutros añadidos para contraste (ajústalos a tu gusto)
val VoxiBg      = Color(0xFF0A0E14)  // fondo
val VoxiSurface = Color(0xFF121821)  // tarjetas / barras
```

**Tipografía:** una sans geométrica y legible (Inter o Poppins vía `fontResource`). Tamaño base grande (los subtítulos se leen a distancia): cuerpo 20–24 sp, picos hasta 34 sp para gritos.

**Pantallas:**
1. **ConversationScreen (principal):** fondo oscuro, burbujas tipo chat que entran con animación (`AnimatedVisibility` + slide/fade), ancladas por carril/posición de hablante. Cada hablante tiene un color base (teal/azul/violeta). El parcial en vivo pulsa suave.
2. **CameraScreen (modo AR):** preview de cámara + burbujas flotando junto a las caras. Botón para alternar con la principal.

**Detalles que la hacen ver cara:**
- Burbujas con esquinas redondeadas (20 dp), leve blur/elevación, borde de 1 dp en el color del hablante.
- Glow sutil en énfasis (sombra de color `#9B84D2`).
- Transiciones de tamaño con `animateFloatAsState` (que el texto crezca suave, no a saltos).
- Indicador "escuchando" minimal (onda o punto pulsante en `#42E8B4`).
- Respeta `prefers reduced motion` / opción para bajar animaciones.
- Targets táctiles grandes, alto contraste (cumple accesibilidad — irónico no hacerlo en una app así).

---

## 9. Permisos, manifest y dependencias

**Permisos (`AndroidManifest.xml`):**
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.INTERNET"/> <!-- solo si usas ElevenLabs -->
```
Pide RECORD_AUDIO y CAMERA en runtime, con pantalla de onboarding que explique por qué.

**`build.gradle` (módulo app) — dependencias clave:**
```gradle
// Compose (usa el BOM de tu versión de Android Studio)
implementation platform('androidx.compose:compose-bom:<actual>')
implementation 'androidx.compose.material3:material3'
implementation 'androidx.activity:activity-compose:<actual>'
implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:<actual>'

// CameraX
implementation 'androidx.camera:camera-camera2:<actual>'
implementation 'androidx.camera:camera-lifecycle:<actual>'
implementation 'androidx.camera:camera-view:<actual>'

// ML Kit Face Mesh
implementation 'com.google.mlkit:face-mesh-detection:<actual>'

// Vosk (reconocimiento offline)
implementation 'com.alphacephei:vosk-android:<actual>'
```
Empaca el modelo español en `app/src/main/assets/model-es/` (descárgalo de alphacephei). Cópialo a almacenamiento interno en el primer arranque.

---

## 10. Robustez / por qué NO va a crashear (mitigaciones)

- **Cámara o ML Kit fallan** → captura el error, oculta el modo cámara, sigue en Modo A. La app nunca depende de la visión para funcionar.
- **Vosk se atasca** → watchdog reinicia el Recognizer; muestra "reanudando…".
- **Sin permiso de cámara** → la app arranca igual en modo carriles.
- **Sin internet** → todo el núcleo es offline; ElevenLabs cae a TTS nativo.
- **Dispositivo lento** → baja la resolución de `ImageAnalysis` (p.ej. 480p) y procesa 1 de cada N frames; el audio siempre tiene prioridad sobre la visión.
- **Memoria del modelo** → usa el modelo *small* de Vosk (~40 MB), no el grande.

---

## 11. Plan de 48 horas (orden de ataque)

1. **Bloque 1 (núcleo):** proyecto Compose + tema con tu paleta + permisos. AudioRecord + Vosk → subtítulos en vivo en pantalla. *No sigas hasta que esto sea sólido.*
2. **Bloque 2 (tono):** ProsodyAnalyzer (RMS, pitch, ritmo) → estilo de las burbujas. Que gritar/susurrar se vea.
3. **Bloque 3 (espacio, Modo A):** carriles + asignar hablante + diarización simple por pitch. Conversación de 2 personas legible.
4. **Bloque 4 (vía de regreso):** ComposeBar + TTS nativo.
5. **Bloque 5 (wow, Modo B):** CameraX + FaceMesh + anclar burbuja a la cara que habla. Con fallback a Modo A.
6. **Bloque 6 (pulido):** animaciones, onboarding, ElevenLabs opcional, grabar el video de demo.

**Si vas tarde:** corta el Bloque 5 (cámara). Con 1–4 ya tienes algo que nadie más tiene (espacio por carriles + tono) y que funciona perfecto.

---

## 12. Criterios de aceptación (la app está lista cuando…)

1. Hablo en español y los subtítulos aparecen continuos, sin cortarse en pausas, offline.
2. Al gritar el texto crece y al susurrar se encoge, en vivo, sin trabar.
3. Dos personas por turnos: cada frase aparece en su carril/lado con su color.
4. Escribo y el teléfono lo dice en voz alta.
5. Tapo la cámara o niego el permiso y la app **no crashea**: sigue subtitulando.
6. La interfaz se ve premium: animaciones suaves, paleta consistente, alto contraste.

---

## 13. Configuración demo-segura (para el escenario)

- Corre en **Modo A o B con solo 2 personas** y guion ensayado.
- Ten el teléfono en un soporte para que la cámara quede fija.
- Lugar sin eco fuerte; el demostrador habla claro y cerca.
- Ten un **plan B grabado en video** del flujo perfecto por si el wifi/escenario falla.
- Frase de pitch: *"Subtitular ya existe. Decir QUIÉN habla, DÓNDE está y CÓMO lo dijo, todo junto y en el teléfono que ya tienes — eso no lo hace nadie."*

---

### Nota final
No prometas "traducción universal" ni "localización de 6 personas". Promete exactamente lo que esta app hace bien: **una conversación viva, espacial y con tono, para personas sordas, offline y gratis.** Eso es honesto, es nuevo en su combinación, y se puede lograr.
