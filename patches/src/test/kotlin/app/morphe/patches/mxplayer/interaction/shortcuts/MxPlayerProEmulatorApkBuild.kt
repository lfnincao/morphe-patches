/*
 * Patches MX Player Pro for emulator install tests (delete shortcut, optional clone).
 *
 * Usage: MxPlayerProEmulatorApkBuildKt <input-apkm-or-apk> <output-signed-apk> [--clone] [--stock] [--resign-only]
 */
package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkMerger
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.logging.NoOpLogger
import app.morphe.patches.all.misc.clone.cloneAppPatch
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

fun main(arguments: Array<String>) {
    require(arguments.size >= 2) {
        "Usage: MxPlayerProEmulatorApkBuildKt <input> <output-signed.apk> [--clone]"
    }
    val clone = arguments.any { it == "--clone" }
    val stockOnly = arguments.any { it == "--stock" }
    val resignOnly = arguments.any { it == "--resign-only" }
    val inputArg = File(arguments[0]).canonicalFile
    val outputApk = File(arguments[1]).canonicalFile
    outputApk.parentFile.mkdirs()

    val apkFile =
        when (inputArg.extension.lowercase()) {
            "zip", "apkm", "xapk", "apks" -> mergeApkmBundle(inputArg)
            "apk" -> inputArg
            else -> error("Unsupported input: ${inputArg.name}")
        }

    val workspace = createTempDirectory("mx-pro-emulator").toFile()
    val unsigned = File(workspace, "unsigned.apk")
    if (stockOnly) {
        apkFile.copyTo(unsigned, overwrite = true)
    } else {
        val patches = when {
            resignOnly -> setOf(mxPlayerPatchedInstallPatch)
            else -> buildSet {
                add(mxPlayerPatchedInstallPatch)
                add(playerDeleteShortcutPatch)
                if (clone) add(cloneAppPatch)
            }
        }

    Patcher(
        PatcherConfig(
            apkFile = apkFile,
            temporaryFilesPath = workspace,
            targetPatcherVersion = "2.3.0",
            validate = false,
        ),
    ).use { patcher ->
        patcher += patches
        runBlocking {
            patcher().collect { result ->
                result.exception?.let { throw it }
                println("Applied: ${result.patch.name}")
            }
        }
        patcher.get().applyTo(unsigned)
    }
    }

    val keyStore = File(workspace, "debug.jks")
    val keyDetails = ApkUtils.KeyStoreDetails(
        keyStore = keyStore,
        alias = "morphe",
        password = "morphe",
    )
    ApkUtils.signApk(unsigned, outputApk, "MorpheTest", keyDetails, minSdkVersion = 24)
    if (!stockOnly) {
        verifyMxPlayerPatchedApkDex(outputApk)
    }
    val pkg = when {
        stockOnly -> "com.mxtech.videoplayer.pro"
        clone -> "com.mxtech.videoplayer.pro.morphe"
        else -> "com.mxtech.videoplayer.pro"
    }
    println("Signed $outputApk (stock=$stockOnly, clone=$clone, package=$pkg)")
}

private fun mergeApkmBundle(bundle: File): File {
    val extractDir = Files.createTempDirectory("mx-pro-apkm").toFile()
    check(
        ProcessBuilder("unzip", "-o", bundle.absolutePath, "-d", extractDir.absolutePath)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0,
    ) { "Failed to extract bundle: $bundle" }
    val mergedApk = File(extractDir.parentFile, "mxplayer-pro-merged-${System.nanoTime()}.apk")
    ApkMerger(NoOpLogger).merge(
        inputFile = extractDir,
        outputFile = mergedApk,
        validateModules = false,
        validateResDir = false,
    )
    println("Merged APKM to $mergedApk")
    return mergedApk
}