package app.docubuds.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.docubuds.core.Donate

/**
 * Donate behaviour, exactly per the Remibuds donate brief:
 * - ACTION_VIEW intent with the exact UPI pay link, wrapped in a chooser
 *   titled "Choose an app to pay with".
 * - Always attempt the chooser, even if the package query returns an empty
 *   list (Android 11+ package visibility can hide installed apps). Only a
 *   chooser attempt that actually throws counts as "no payment app" — the
 *   caller then shows the fallback sheet.
 * - Copy puts the exact UPI ID on the clipboard with label "UPI ID" and shows
 *   the toast "UPI ID copied."
 * The app never sees, stores or handles the payment itself.
 */
object DonateActions {
    /** Returns true if the chooser was launched; false if it threw (show fallback). */
    fun openUpiChooser(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Donate.UPI_LINK))
            val chooser = Intent.createChooser(intent, Donate.CHOOSER_TITLE)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openInstagram(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Donate.INSTAGRAM_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun copyUpiId(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("UPI ID", Donate.UPI_ID))
        Toast.makeText(context, Donate.COPY_TOAST, Toast.LENGTH_SHORT).show()
    }
}
