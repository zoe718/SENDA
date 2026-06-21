package com.voxi.captions.vision

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import kotlin.math.abs

/**
 * Analizador de CameraX que corre ML Kit Face Mesh on-device (spec §1/§6) y
 * entrega la lista de caras visibles con su posición y apertura de boca.
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
            mouthOpen = mouthOpen,
        )
    }

    companion object {
        private const val UPPER_LIP = 13
        private const val LOWER_LIP = 14
    }
}
