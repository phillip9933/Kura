package app.kura.nativeapp

import androidx.compose.runtime.Composable

internal const val enhancedCardScanning = false
@Composable internal fun EnhancedCardDialog(vm: VaultController, bytes: ByteArray, target: ImageTarget, dismiss: () -> Unit, retake: () -> Unit) = Unit

internal fun recognizeWorkerFile(context: android.content.Context, directory: java.io.File, input: java.io.File): String =
    error("Enhanced scanning is disabled")
