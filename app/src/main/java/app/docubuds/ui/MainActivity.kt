package app.docubuds.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.docubuds.android.ReminderWorker
import kotlinx.coroutines.launch

/**
 * Single activity. Responsibilities:
 * - BiometricPrompt unlock using ONLY the phone's lock (BIOMETRIC_STRONG |
 *   DEVICE_CREDENTIAL). The app never reads or stores the phone password.
 * - If no phone lock is set, the lock screen blocks with instructions.
 * - 1-minute inactivity lock, checked on resume.
 * - Recent-apps thumbnail blanking WITHOUT FLAG_SECURE (screenshots are
 *   allowed): onPause switches the UI to a blank screen, so the thumbnail
 *   captured for the app switcher shows nothing. The blank screen is removed
 *   on resume (after the lock check). Best-effort — see README.
 */
class MainActivity : FragmentActivity() {
    private val vm: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderWorker.schedule(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val blank by vm.blankForRecents.collectAsState()
                    if (blank) {
                        // Blank screen for the recent-apps thumbnail.
                        Box(modifier = Modifier.fillMaxSize(), content = {})
                    } else {
                        DocuBudsRoot(vm, ::authenticate, ::openSecuritySettings, ::isDeviceSecure)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Blank the UI before the system captures the recent-apps thumbnail.
        vm.setBlankForRecents(true)
    }

    override fun onResume() {
        super.onResume()
        vm.setBlankForRecents(false)
        vm.checkInactivityLock()
    }

    fun isDeviceSecure(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }

    fun openSecuritySettings() {
        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    /** Shows the phone's own unlock prompt. No CryptoObject: device-credential
     *  authentication cannot use one, and the vault keys are KeyStore-bound. */
    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lifecycleScope.launch { onSuccess() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onError("Authentication failed. Try again.")
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock DocuBuds")
            .setSubtitle("Use your phone lock to open your vault")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}
