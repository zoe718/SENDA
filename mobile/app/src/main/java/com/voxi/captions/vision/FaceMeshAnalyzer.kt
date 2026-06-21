package com.voxi.captions.vision

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Analizador de CameraX que corre ML Kit Face Mesh on-device (spec §1/§6) y
 * entrega la lista de caras visibles con su posición, apertura de boca y una
 * firma geométrica del rostro para reconocerlo entre cuadros.
 *
 * Robustez (spec §10): cualquier fallo de ML Kit se traga y reporta "sin caras";
 * nunca propaga excepciones que tumben la app. El audio siempre tiene prioridad.
 */
class FaceMeshAnalyzer(
    private val onFaces: (List<DetectedFace>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceMeshDetection.getClient(
        FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = rotation == 90 || rotation == 270
        val uprightWidth = (if (rotated) imageProxy.height else imageProxy.width).toFloat()
        val uprightHeight = (if (rotated) imageProxy.width else imageProxy.height).toFloat()

        val image = InputImage.fromMediaImage(media, rotation)
        detector.process(image)
            .addOnSuccessListener { meshes ->
                runCatching {
                    onFaces(meshes.mapNotNull { toDetectedFace(it, uprightWidth, uprightHeight) })
                }
            }
            .addOnFailureListener { onFaces(emptyList()) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun toDetectedFace(mesh: FaceMesh, width: Float, height: Float): DetectedFace? {
        if (width <= 0f || height <= 0f) return null
        val box = mesh.boundingBox
        val points = mesh.allPoints
        if (points.size <= LOWER_LIP) return null

        // Índices del mallado MediaPipe: 13 = labio superior interno, 14 = inferior.
        val upper = points[UPPER_LIP].position
        val lower = points[LOWER_LIP].position
        val faceHeight = box.height().toFloat().coerceAtLeast(1f)
        val mouthOpen = (abs(lower.y - upper.y) / faceHeight).coerceIn(0f, 1f)

        return DetectedFace(
            cx = (box.exactCenterX() / width).coerceIn(0f, 1f),
            cy = (box.exactCenterY() / height).coerceIn(0f, 1f),
            widthRatio = (box.width().toFloat() / width).coerceIn(0f, 1f),
            heightRatio = (box.height().toFloat() / height).coerceIn(0f, 1f),
            mouthOpen = mouthOpen,
            signature = signatureOf(mesh),
        )
    }

    /**
     * Firma geométrica del rostro: proporciones faciales normalizadas por la
     * distancia entre ojos (interocular), invariantes a escala y posición. No es
     * una huella biométrica fuerte, pero distingue razonablemente a unas pocas
     * personas en cuadro y ayuda a re-identificarlas tras una oclusión breve.
     * Devuelve null si los puntos clave no son fiables.
     */
    private fun signatureOf(mesh: FaceMesh): FloatArray? = runCatching {
        val p = mesh.allPoints
        val maxIdx = intArrayOf(
            EYE_L_OUTER, EYE_L_INNER, EYE_R_INNER, EYE_R_OUTER,
            NOSE_TIP, NOSE_BRIDGE, MOUTH_L, MOUTH_R, CHIN, FOREHEAD,
        ).max()
        if (p.size <= maxIdx) return@runCatching null

        fun x(i: Int) = p[i].position.x
        fun y(i: Int) = p[i].position.y
        fun dist(a: Int, b: Int) = hypot((x(a) - x(b)).toDouble(), (y(a) - y(b)).toDouble()).toFloat()

        // Escala de referencia: distancia entre las esquinas externas de los ojos.
        val ocular = dist(EYE_L_OUTER, EYE_R_OUTER)
        if (ocular < 1f) return@runCatching null

        val sig = floatArrayOf(
            dist(MOUTH_L, MOUTH_R) / ocular,        // ancho de boca relativo
            dist(NOSE_BRIDGE, NOSE_TIP) / ocular,   // largo de nariz relativo
            dist(NOSE_TIP, MOUTH_L) / ocular,       // nariz↔boca (mitad izq)
            dist(EYE_L_OUTER, EYE_L_INNER) / ocular, // ancho ojo izquierdo
            dist(EYE_R_OUTER, EYE_R_INNER) / ocular, // ancho ojo derecho
            dist(FOREHEAD, CHIN) / ocular,          // alto facial relativo
        )
        if (sig.any { it.isNaN() || it.isInfinite() }) null else sig
    }.getOrNull()

    companion object {
        private const val UPPER_LIP = 13
        private const val LOWER_LIP = 14
        // Puntos canónicos del mallado MediaPipe (468) para la firma facial.
        private const val EYE_L_OUTER = 33
        private const val EYE_L_INNER = 133
        private const val EYE_R_INNER = 362
        private const val EYE_R_OUTER = 263
        private const val NOSE_TIP = 1
        private const val NOSE_BRIDGE = 168
        private const val MOUTH_L = 61
        private const val MOUTH_R = 291
        private const val CHIN = 152
        private const val FOREHEAD = 10
    }
}
