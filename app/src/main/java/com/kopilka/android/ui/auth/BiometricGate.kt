package com.kopilka.android.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun authenticate(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val canAuth = BiometricManager.from(activity)
        .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)

    // No lock screen configured — proceed without blocking
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onSuccess()
        return
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onSuccess()

            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code in listOf(
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    )
                ) {
                    onSuccess() // Device has no auth capability — let through
                } else {
                    onCancel()
                }
            }

            override fun onAuthenticationFailed() = Unit // stays on lock screen
        },
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kopilka")
            .setSubtitle("Verify your identity to access the budget")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
    )
}
