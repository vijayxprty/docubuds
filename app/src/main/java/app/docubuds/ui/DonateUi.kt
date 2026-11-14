package app.docubuds.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.docubuds.R
import app.docubuds.android.DonateActions
import app.docubuds.core.Donate

/**
 * Donate UI — exact copy, placement and behaviour from the Remibuds donate
 * brief (see Donate-Feature-for-ChatGPT.md). "Remibuds" is replaced by
 * "DocuBuds" only; nothing else changes.
 *
 * The capsule-with-heart illustration from art-donate.svg is redrawn as a
 * small Compose canvas (the SVG itself is kept unchanged in assets). The QR
 * code is the exact donate-qr.png from Remibuds, shown at 240x240.
 */

/** Friendly capsule character holding a heart, drawn to match art-donate.svg. */
@Composable
fun DonateArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Capsule body (teal, two halves)
        drawRoundRect(
            color = Color(0xFF2A9D8F),
            topLeft = Offset(w * 0.18f, h * 0.15f),
            size = Size(w * 0.64f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.32f, w * 0.32f)
        )
        drawRoundRect(
            color = Color(0xFFE76F51),
            topLeft = Offset(w * 0.18f, h * 0.15f),
            size = Size(w * 0.32f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.32f, w * 0.32f)
        )
        // Heart
        val cx = w * 0.78f
        val cy = h * 0.38f
        val s = w * 0.13f
        val heart = Path().apply {
            moveTo(cx, cy + s)
            cubicTo(cx - s * 1.6f, cy - s * 0.2f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.35f)
            cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.6f, cy - s * 0.2f, cx, cy + s)
        }
        drawPath(heart, color = Color(0xFFE63946))
    }
}

/** Header heart button — on every screen. Accessibility label per the brief. */
@Composable
fun DonateHeartButton(onDonate: () -> Unit) {
    IconButton(onClick = onDonate) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Donate to the developer",
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Runs the donate behaviour: try the UPI chooser; if it throws, the caller
 * shows the fallback sheet (handled here via [onFallback]).
 */
@Composable
fun rememberDonateLauncher(): (Context) -> Unit {
    var showFallback by remember { mutableStateOf(false) }
    if (showFallback) {
        DonateFallbackDialog(onDismiss = { showFallback = false })
    }
    val context = LocalContext.current
    return { ctx: Context ->
        if (!DonateActions.openUpiChooser(ctx)) showFallback = true
    }
}

/** Small donate row — bottom of every screen except More/Settings. */
@Composable
fun DonateRow(onDonate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDonate() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DonateArt(modifier = Modifier.size(72.dp, 44.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Donate to the developer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Pay any amount · helps keep DocuBuds running and ad-free",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/** Full donate section — bottom of the More/Settings screen, in order. */
@Composable
fun DonateSection(onDonate: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Donate to the developer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            DonateArt(modifier = Modifier.size(160.dp, 90.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                painter = painterResource(id = R.drawable.donate_qr),
                contentDescription = "QR code — scan to pay with any UPI app",
                modifier = Modifier
                    .size(240.dp)
                    .clickable { onDonate() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Scan to pay with any UPI app · or tap the code to pay.", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onDonate, modifier = Modifier.fillMaxWidth()) {
                Text("Click to pay")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("UPI ID: ${Donate.UPI_ID}", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { DonateActions.copyUpiId(context) }) { Text("Copy") }
            }
            Text(
                "Your donation helps keep DocuBuds running and ad-free.",
                fontSize = 14.sp
            )
        }
    }
}

/** Fallback sheet — shown when no UPI app can be opened. Exact wording per the brief. */
@Composable
fun DonateFallbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Donate to the developer") },
        text = {
            Column {
                Text("Pay any amount you like, from any UPI app. Your donation helps keep DocuBuds running and ad-free.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("UPI ID: ${Donate.UPI_ID}")
            }
        },
        confirmButton = {
            Button(onClick = { DonateActions.copyUpiId(context); onDismiss() }) {
                Text("Copy UPI ID")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/** Footer credit — the very last line on every screen. Handle links to Instagram. */
@Composable
fun CreditLine() {
    val context = LocalContext.current
    val annotated = buildAnnotatedString {
        append("Made with love ♥ by Vijay Mamta Sahani (")
        pushStringAnnotation(tag = "instagram", annotation = Donate.INSTAGRAM_URL)
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
            append(Donate.INSTAGRAM_HANDLE)
        }
        pop()
        append(")")
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = { offset ->
            annotated.getStringAnnotations("instagram", offset, offset).firstOrNull()?.let {
                DonateActions.openInstagram(context)
            }
        }
    )
}

/** Short privacy line, kept separate from and directly above the credit line. */
@Composable
fun PrivacyLine() {
    Text(
        text = "Private, on this device. Your records reflect what you report.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** Donate block + privacy line + credit, in the required order, at the bottom of a screen. */
@Composable
fun DonateFooter(isMoreScreen: Boolean, onDonate: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isMoreScreen) DonateSection(onDonate) else DonateRow(onDonate)
        PrivacyLine()
        CreditLine()
    }
}
