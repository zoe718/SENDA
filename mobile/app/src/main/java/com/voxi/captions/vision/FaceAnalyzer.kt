package com.voxi.captions.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Analizador de CameraX que corre ML Kit **Face Detection** on-device (spec §6).
 *
 * A diferencia de Face Mesh (que solo seguia bien a UNA cara prominente), Face
 * Detection entrega TODAS las caras del cuadro con:
 *  - un [Face.trackingId] estable y nativo (tracking on-device) -> [DetectedFace.trackId];
 *  - landmarks (ojos, nariz, comisuras y base de la boca) con los que calculamos
 *    una firma geometrica para re-identificar a la persona (escaneo <-> charla);
 *  - una estimacion de apertura de boca ([mouthOpen]) para saber quien habla.
 *
 * En modo escaneo ([captureThumbnails] devuelve true) recorta ademas una
 * miniatura nitida de cada rostro para enrolar a la persona con su foto.
 *
 * Robustez (spec §10): cualquier fallo de ML Kit se traga y reporta "sin caras";
 * nunca propaga excepciones. El audio siempre tiene prioridad.
 */
class FaceAnalyzer(
    private val onFaces: (List<DetectedFace>) -> Unit,
    private val captureThumbnails: () -> Boolean = { false },
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // FAST: tiempo real y, sobre todo, MISMOS landmarks tanto en escaneo
            // como en conversacion, para que las firmas sean comparables.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            // Caras relativamente pequenas para captar a varias personas a la vez.
            .setMinFaceSize(0.1f)
            // Ids de tracking nativos y estables durante la sesion.
            .enableTracking()
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

        // Solo en modo escaneo convertimos el frame a Bitmap (operacion costosa)
        // para poder recortar la miniatura de cada rostro.
        val upright: Bitmap? = if (captureThumbnails()) {
            runCatching { rotateUpright(imageProxy.toBitmap(), rotation) }.getOrNull()
        } else {
            null
        }

        val image = InputImage.fromMediaImage(media, rotation)
        detector.process(image)
            .addOnSuccessListener { faces ->
                runCatching {
                    onFaces(faces.mapNotNull { toDetectedFace(it, uprightWidth, uprightHeight, upright) })
                }
            }
            .addOnFailureListener { onFaces(emptyList()) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun toDetectedFace(
        face: Face,
        width: Float,
        height: Float,
        upright: Bitmap?,
    ): DetectedFace? {
        if (width <= 0f || height <= 0f) return null
        val box = face.boundingBox
        val faceHeight = box.height().toFloat().coerceAtLeast(1f)
        return DetectedFace(
            cx = (box.exactCenterX() / width).coerceIn(0f, 1f),
            cy = (box.exactCenterY() / height).coerceIn(0f, 1f),
            widthRatio = (box.width().toFloat() / width).coerceIn(0f, 1f),
            heightRatio = (box.height().toFloat() / height).coerceIn(0f, 1f),
            mouthOpen = mouthOpenOf(face, faceHeight),
            signature = signatureOf(face),
            trackId = face.trackingId ?: -1,
            // Face Detection no da contorno en multi-cara; el aura del hablante
            // cae a un ovalo del tamano de la cara (CameraScreen ya lo soporta).
            contour = null,
            imageAspect = width / height,
            thumbnail = upright?.let { cropThumbnail(it, box) },
        )
    }

    /**
     * Apertura de boca aproximada: cuanto cae la base del labio inferior respecto
     * a la linea de las comisuras, normalizado por el alto de la cara. No es exacta,
     * pero captura el MOVIMIENTO labial (abrir/cerrar), que es justo lo que usa
     * [ActiveSpeaker] para elegir al hablante.
     */
    private fun mouthOpenOf(face: Face, faceHeight: Float): Float {
        val l = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val r = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val b = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        if (l == null || r == null || b == null) return 0f
        val midY = (l.y + r.y) / 2f
        return (abs(b.y - midY) / faceHeight).coerceIn(0f, 1f)
    }

    /**
     * Firma geometrica del rostro a partir de los landmarks de Face Detection,
     * normalizada por la distancia interocular (invariante a escala/posicion) y
     * centrada en su media para que el coseno compare la FORMA del rostro (mas
     * discriminante que ratios crudos, todos positivos). null si faltan puntos.
     */
    private fun signatureOf(face: Face): FloatArray? = runCatching {
        val le = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return@runCatching null
        val re = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return@runCatching null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return@runCatching null
        val ml = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return@runCatching null
        val mr = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return@runCatching null
        val mb = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position

        fun d(a: PointF, b: PointF) =
            hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

        val ocular = d(le, re)
        if (ocular < 1f) return@runCatching null
        val eyeMid = PointF((le.x + re.x) / 2f, (le.y + re.y) / 2f)
        val mouthMid = PointF((ml.x + mr.x) / 2f, (ml.y + mr.y) / 2f)
        val bottom = mb ?: mouthMid

        val raw = floatArrayOf(
            d(ml, mr) / ocular,        // ancho de boca relativo
            d(eyeMid, nose) / ocular,  // ojos -> nariz
            d(nose, mouthMid) / ocular, // nariz -> boca
            d(eyeMid, bottom) / ocular, // alto facial (ojos -> menton aprox)
            d(nose, ml) / ocular,      // nariz -> comisura izq
            d(nose, mr) / ocular,      // nariz -> comisura der
            d(nose, bottom) / ocular,  // largo nariz -> labio inferior
        )
        if (raw.any { it.isNaN() || it.isInfinite() }) return@runCatching null

        // Centra en la media -> el coseno mide forma (correlacion), no magnitud.
        val mean = raw.average().toFloat()
        FloatArray(raw.size) { raw[it] - mean }
    }.getOrNull()

    /** Gira el bitmap del sensor a posicion vertical para que coincida con las coords. */
    private fun rotateUpright(bmp: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return bmp
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Recorta el rostro (con margen) y lo reduce a una miniatura nitida ~320 px. */
    private fun cropThumbnail(src: Bitmap, box: Rect): Bitmap? = runCatching {
        val padX = box.width() * 0.22f
        val padY = box.height() * 0.30f
        val l = (box.left - padX).toInt().coerceIn(0, src.width - 1)
        val t = (box.top - padY).toInt().coerceIn(0, src.height - 1)
        val r = (box.right + padX).toInt().coerceIn(l + 1, src.width)
        val b = (box.bottom + padY).toInt().coerceIn(t + 1, src.height)
        val w = r - l
        val h = b - t
        if (w < 8 || h < 8) return@runCatching null
        val crop = Bitmap.createBitmap(src, l, t, w, h)
        val max = 320
        val longest = maxOf(w, h)
        if (longest <= max) {
            crop
        } else {
            val scale = max.toFloat() / longest
            Bitmap.createScaledBitmap(crop, (w * scale).toInt(), (h * scale).toInt(), true)
        }
    }.getOrNull()
}
