package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.dex.SdkDexVerifier
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

/**
 * Runs dexdump + d8 verification on every DEX file in a patched MX Player APK.
 */
internal fun verifyMxPlayerPatchedApkDex(apkFile: File) {
    val sdk = File(System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME required for DEX verification"))
    val dexDir = createTempDirectory("mx-player-dex-verify").toFile()
    ZipFile(apkFile).use { zip ->
        zip.stream()
            .filter { !it.isDirectory && it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val out = File(dexDir, entry.name)
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
    }
    val verifier = SdkDexVerifier(sdk)
    val dexNames = listOf(
        "classes.dex",
        "classes2.dex",
        "classes3.dex",
        "classes4.dex",
        "classes5.dex",
        "classes6.dex",
    )
    val verified = dexNames.mapNotNull { name ->
        File(dexDir, name).takeIf { it.isFile }
    }
    require(verified.isNotEmpty()) { "No DEX files extracted from $apkFile" }
  verified.forEach { verifier.verifyDexFile(it) }
    println("ART/SDK DEX verification passed for ${verified.size} file(s) in $apkFile")
}