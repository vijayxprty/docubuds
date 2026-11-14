package app.docubuds.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.docubuds.core.VaultRecord
import java.io.File

/**
 * Internal offline viewers (spec §5): images are decoded from the decrypted
 * bytes in memory; PDFs are rendered with the built-in PdfRenderer. Files are
 * never handed to an external viewer automatically.
 */
@Composable
fun FileViewerDialog(vm: VaultViewModel, record: VaultRecord, onDismiss: () -> Unit) {
    val bytes = remember(record.fileId) { record.fileId?.let { vm.readAttachmentBytes(it) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.fileName ?: record.title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    bytes == null -> Text("This file could not be read.")
                    record.mimeType == "application/pdf" || record.fileName?.endsWith(".pdf", ignoreCase = true) == true ->
                        PdfViewer(bytes)
                    record.mimeType?.startsWith("image/") == true ->
                        ImageViewer(bytes)
                    else -> Text(
                        "This file type (${record.mimeType ?: "unknown"}) has no in-app viewer. " +
                            "Use Share or Export to open it in another app."
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ImageViewer(bytes: ByteArray) {
    val bitmap by remember(bytes) {
        mutableStateOf(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Document image",
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Text("This image could not be displayed.")
    }
}

@Composable
fun PdfViewer(bytes: ByteArray) {
    val context = LocalContext.current
    val pages by remember(bytes) {
        mutableStateOf(renderPdfPages(context, bytes))
    }
    if (pages.isEmpty()) {
        Text("This PDF could not be rendered.")
    } else {
        LazyColumn {
            items(pages) { page ->
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = "PDF page",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
        }
    }
}

private fun renderPdfPages(context: android.content.Context, bytes: ByteArray): List<Bitmap> {
    return try {
        val tmp = File(context.cacheDir, "view-${System.currentTimeMillis()}.pdf")
        tmp.writeBytes(bytes)
        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pages = mutableListOf<Bitmap>()
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pages += bitmap
        }
        renderer.close()
        pfd.close()
        tmp.delete()
        pages
    } catch (_: Exception) {
        emptyList()
    }
}
