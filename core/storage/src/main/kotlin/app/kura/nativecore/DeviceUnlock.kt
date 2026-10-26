package app.kura.nativecore

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** An optional device-local wrapper. The authenticated wrapper remains available for recovery. */
class DeviceUnlock(context: Context, allowSoftwareForTests: Boolean = false) {
    private val envelope = AtomicFile(File(context.noBackupFilesDir, "native-device-unlock-envelope"))
    private val hardware = HardwareVaultKey(
        context,
        alias = "app.kura.wallet.native.master.device-unlock.v1",
        allowSoftwareForTests = allowSoftwareForTests,
        requireAuthentication = false,
    )

    val enabled: Boolean get() = envelope.baseFile.exists()

    fun enable(master: SensitiveBytes) {
        hardware.provision()
        val cipher = hardware.cipher(true)
        val wrapped = hardware.wrap(cipher, master)
        // Verify the device can reopen this key before publishing the optional wrapper.
        val probe = hardware.unwrap(hardware.cipher(false, cipher.iv), wrapped)
        try {
            check(master.useBytes { MessageDigest.isEqual(it, probe) }) { "Device unlock verification failed" }
        } finally {
            probe.fill(0)
        }
        val document = JSONObject().put("iv", Envelope.encode(cipher.iv)).put("wrapped", Envelope.encode(wrapped))
        VerifiedAtomicWrite.write(envelope, document.toString().toByteArray(Charsets.UTF_8))
    }

    fun open(): ByteArray {
        val document = envelope.openRead().use {
            JSONObject(Envelope.readBounded(it, 2048).toString(Charsets.UTF_8))
        }
        return hardware.unwrap(
            hardware.cipher(false, Envelope.decode(document.getString("iv"))),
            Envelope.decode(document.getString("wrapped")),
        )
    }

    fun disable() {
        envelope.delete()
        check(!enabled) { "Unable to remove phone-unlock access" }
        hardware.delete()
    }
}
