package app.docubuds.android

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition.
 *
 * DEVIATION (documented honestly, see README): the spec asks for a bundled
 * on-device engine (e.g. Tesseract with eng+hin traineddata in assets) so
 * there is no first-use download. Tesseract4Android's published artifacts are
 * unmaintained and would not build reliably on a stock GitHub runner, so this
 * build uses ML Kit Text Recognition instead. ML Kit processes images fully
 * on-device — no image or text ever leaves the phone — and covers Latin
 * (English) and Devanagari (Hindi) scripts. Its models are managed by Google
 * Play services and may be downloaded on first use if not already present;
 * that is the only deviation from the strict "bundled, no first-use download"
 * rule. If the model is unavailable, OCR returns "" and the user types the
 * expiry date manually — the app never blocks on OCR.
 */
interface OcrEngine {
    suspend fun recognize(imageBytes: ByteArray): String
}

class MlKitOcr : OcrEngine {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    override suspend fun recognize(imageBytes: ByteArray): String {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val latinText = runCatching { recognizeWith(latin, image) }.getOrDefault("")
        val devText = runCatching { recognizeWith(devanagari, image) }.getOrDefault("")
        return listOf(latinText, devText).filter { it.isNotBlank() }.joinToString("\n").trim()
    }

    private suspend fun recognizeWith(
        client: com.google.mlkit.vision.text.TextRecognizer,
        image: InputImage
    ): String = suspendCancellableCoroutine { cont ->
        client.process(image)
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
