package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.apk.ApkUtils
import java.io.File

private val signingKeyStore =
    File("/agent/repos/morphe-patches/.artifacts/mxplayer-emulator-signing.jks")

internal fun mxPlayerEmulatorKeyStoreDetails(): ApkUtils.KeyStoreDetails {
    if (!signingKeyStore.exists()) {
        signingKeyStore.parentFile?.mkdirs()
        check(
            ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-v",
                "-keystore",
                signingKeyStore.absolutePath,
                "-alias",
                "morphe",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "10000",
                "-storepass",
                "morphe",
                "-keypass",
                "morphe",
                "-dname",
                "CN=Morphe MX Player Emulator Test",
            )
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0,
        ) { "Failed to create ${signingKeyStore.absolutePath}" }
        println("Created persistent test keystore at $signingKeyStore")
    }
    return ApkUtils.KeyStoreDetails(
        keyStore = signingKeyStore,
        alias = "morphe",
        password = "morphe",
    )
}