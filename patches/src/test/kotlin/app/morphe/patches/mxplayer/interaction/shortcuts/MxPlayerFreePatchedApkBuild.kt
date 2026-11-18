/*
 * Applies free MX Player patches (delete shortcut, ad removal, patched install) to a stock APK.
 *
 * Usage: MxPlayerFreePatchedApkBuildKt <input.apk> [output.apk]
 */
package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patches.mxplayer.ad.mxPlayerRemoveAdsPatch
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

fun main(arguments: Array<String>) {
    require(arguments.isNotEmpty()) {
        "Usage: MxPlayerFreePatchedApkBuildKt <input.apk> [output.apk]"
    }
    val apkFile = File(arguments[0]).canonicalFile
    require(apkFile.isFile) { "Input does not exist: $apkFile" }
    val output = arguments.getOrNull(1)?.let { File(it).canonicalFile }
    output?.parentFile?.mkdirs()

    val workspace = createTempDirectory("mx-free-patch").toFile()
    val patches = setOf(
        mxPlayerPatchedInstallPatch,
        playerDeleteShortcutPatch,
        mxPlayerRemoveAdsPatch,
    )

    Patcher(
        PatcherConfig(
            apkFile = apkFile,
            temporaryFilesPath = workspace,
            targetPatcherVersion = "2.3.0",
            validate = true,
        ),
    ).use { patcher ->
        patcher += patches
        runBlocking {
            patcher().collect { result ->
                result.exception?.let { throw it }
                println("Applied: ${result.patch.name}")
            }
        }
        val patched = patcher.get()
        output?.let { out ->
            val unsigned = File(out.parentFile, "${out.nameWithoutExtension}.unsigned.apk")
            patched.applyTo(unsigned)
            ApkUtils.signApk(unsigned, out, "MorpheDeviceTest", mxPlayerEmulatorKeyStoreDetails(), minSdkVersion = 24)
            unsigned.delete()
            println(verifyMxPlayerFreePatchedApkDex(out))
            println(verifyMxPlayerFreeCustomizeMenu(out))
            println(verifyMxPlayerExtensionInvokes(out))
            publishDeviceTestArtifact(out)
        }
        println("MX Player free patch succeeded (input=$apkFile)")
    }
}

private fun publishDeviceTestArtifact(signedApk: File) {
    val repoArtifacts = File("/agent/repos/morphe-patches/.artifacts").also { it.mkdirs() }
    val baseName = "mx-free-3.5.3-delete-device-test"
    val destApk = File(repoArtifacts, "$baseName.apk")
    if (signedApk.absolutePath != destApk.absolutePath) {
        signedApk.inputStream().use { input ->
            destApk.outputStream().use { output -> input.copyTo(output) }
        }
    }
    val gz = File(repoArtifacts, "$baseName.apk.gz")
    val gzip = ProcessBuilder("gzip", "-c", destApk.absolutePath)
        .redirectErrorStream(true)
        .start()
    gzip.inputStream.use { input ->
        gz.outputStream().use { output -> input.copyTo(output) }
    }
    check(gzip.waitFor() == 0) { "gzip failed for ${destApk.absolutePath}" }
    val meta = File(repoArtifacts, "$baseName.meta.json")
    meta.writeText(
        """
        {
          "package": "com.mxtech.videoplayer.ad",
          "versionName": "3.5.3",
          "abi": "arm64-v8a",
          "signedApkBytes": ${destApk.length()},
          "gzipApkBytes": ${gz.length()},
          "signedApk": "${destApk.absolutePath}",
          "gzipApk": "${gz.absolutePath}",
          "install": "adb install -r ${destApk.absolutePath}"
        }
        """.trimIndent(),
    )
    val pointerDir = File("/opt/cursor/artifacts")
    pointerDir.mkdirs()
    val pointer = File(pointerDir, "$baseName.meta.json")
    pointer.writeText(meta.readText())
    println("Device test APK: ${destApk.absolutePath}")
    println("Compressed APK: ${gz.absolutePath} (${gz.length()} bytes)")
    println("Artifact pointer: ${pointer.absolutePath}")
}