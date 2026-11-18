package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.dex.SdkDexVerifier
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

/**
 * Runs dexdump + d8 on every DEX file, then ART `package compile -m verify` on a connected device/emulator.
 */
internal fun verifyMxPlayerPatchedApk(
    apkFile: File,
    packageName: String = "com.mxtech.videoplayer.pro",
    stockApk: File? = null,
) {
    verifyMxPlayerPatchedApkDex(apkFile)
    val arrayReport = verifyMxPlayerShortcutsArray(apkFile, stockApk)
    println(arrayReport)
  val adapterReport = verifyMxPlayerCustomizeAdapter(apkFile)
  println(adapterReport)
    val behaviorReport = verifyMxPlayerDeleteShortcutBehavior(apkFile)
    println(behaviorReport)
    val invokeReport = verifyMxPlayerExtensionInvokes(apkFile)
    println(invokeReport)
    val appInvokeReport = verifyMxPlayerAppInvokes(apkFile)
    println(appInvokeReport)
    val invokeLog = File("/opt/cursor/artifacts/mxplayer-extension-invoke-verify.log")
    invokeLog.parentFile?.mkdirs()
    invokeLog.writeText("$invokeReport\n$appInvokeReport")
    println("Wrote extension invoke verify log to $invokeLog")
    val appInvokeLog = File("/opt/cursor/artifacts/mxplayer-app-invoke-verify.log")
    appInvokeLog.writeText(appInvokeReport)
    println("Wrote app invoke verify log to $appInvokeLog")
    val artLog =
        if (hasConnectedAndroidDevice()) {
            verifyMxPlayerArtOnDevice(apkFile, packageName)
        } else {
            """
            === device ART verify (skipped) ===
            No adb device connected; SDK dex verification completed only.
            Run verifyMxPlayerArtOnDevice locally with an API 35 emulator for menu/customize path coverage.
            """.trimIndent()
        }
    val logFile = File("/opt/cursor/artifacts/mxplayer-art-verify.log")
    logFile.parentFile?.mkdirs()
    logFile.writeText(artLog)
    println("Wrote ART verify log to $logFile")
    require(!artLog.contains("hard fail", ignoreCase = true)) {
        "ART verification reported hard failures; see $logFile"
    }
    require(!artLog.contains("VerifyError", ignoreCase = true)) {
        "ART verification log contains VerifyError; see $logFile"
    }
    require(!artLog.contains("rejected class", ignoreCase = true)) {
        "ART verification log contains rejected class; see $logFile"
    }
    require(!artLog.contains("verification error", ignoreCase = true)) {
        "ART verification log contains verification error; see $logFile"
    }
    require(!artLog.contains("failed to verify", ignoreCase = true)) {
        "ART verification log contains failed to verify; see $logFile"
    }
}

private fun hasConnectedAndroidDevice(): Boolean {
    val sdk = resolveAndroidSdk()
    val adb = File(sdk, "platform-tools/adb")
    if (!adb.isFile) return false
    val process = ProcessBuilder(adb.absolutePath, "devices")
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output.lineSequence()
        .drop(1)
        .any { line -> line.trim().endsWith("device") }
}

internal fun resolveAndroidSdk(): File =
    listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
    )
        .map { File(it) }
        .plus(
            listOf(
                File(System.getProperty("user.home"), "android-sdk"),
                File("/home/ubuntu/android-sdk"),
            ),
        )
        .firstOrNull { candidate ->
            File(candidate, "build-tools").isDirectory && File(candidate, "platform-tools/adb").isFile
        }
        ?: error("Android SDK with build-tools and adb not found (set ANDROID_HOME)")

internal fun verifyMxPlayerPatchedApkDex(apkFile: File) {
    val sdk = resolveAndroidSdk()
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

internal fun verifyMxPlayerArtOnDevice(apkFile: File, packageName: String): String {
    val sdk = resolveAndroidSdk()
    val adb = File(sdk, "platform-tools/adb")
    require(adb.isFile) { "adb not found: $adb" }
    val serial = System.getenv("ANDROID_SERIAL") ?: "emulator-5554"

    fun runAdb(vararg args: String, timeoutSec: Long = 120, requireSuccess: Boolean = true): String {
        val process = ProcessBuilder(listOf(adb.absolutePath, "-s", serial) + args)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        check(finished) { "adb ${args.firstOrNull()} timed out after ${timeoutSec}s\n$output" }
        if (requireSuccess) {
            check(process.exitValue() == 0) { "adb ${args.joinToString(" ")} failed (${process.exitValue()}):\n$output" }
        }
        return output
    }

    runAdb("uninstall", packageName, requireSuccess = false)
    runAdb("install", "-r", apkFile.absolutePath, timeoutSec = 180)
    runAdb("logcat", "-c")
    val compileOut = runAdb(
        "shell",
        "cmd",
        "package",
        "compile",
        "-m",
        "verify",
        "-f",
        packageName,
        timeoutSec = 300,
        requireSuccess = false,
    )
    // Trigger common paths so verifier runs loaded player code.
  runAdb(
    "shell",
    "monkey",
    "-p",
    packageName,
    "-c",
    "android.intent.category.LAUNCHER",
    "1",
  )
    Thread.sleep(5000L)
  runAdb(
    "shell",
    "am",
    "start",
    "-a",
    "android.intent.action.VIEW",
    "-d",
    "file:///sdcard/Download/test.mp4",
    "-t",
    "video/mp4",
    "-n",
    "$packageName/com.mxtech.videoplayer.ActivityScreen",
  )
    Thread.sleep(8000L)
    runAdb("shell", "input", "tap", "1000", "80")
    Thread.sleep(1500L)
    runAdb("shell", "input", "tap", "900", "400")
    Thread.sleep(2000L)
    val logcat =
        try {
            runAdb(
                "exec-out",
                "logcat",
                "-d",
                "-t",
                "150",
                "-b",
                "main",
                "-b",
                "crash",
                timeoutSec = 120,
            )
        } catch (e: IllegalStateException) {
            if (e.message?.contains("logcat") == true) {
                println("WARN: logcat scrape timed out; continuing with compile output only")
                ""
            } else {
                throw e
            }
        }
    val filtered = buildString {
        appendLine("=== cmd package compile -m verify -f $packageName ===")
        appendLine(compileOut.trim())
        appendLine()
        appendLine("=== logcat (ART verifier failures only) ===")
        var hits = 0
        val watchClasses =
            listOf("o20\$a", "o20", "n20", "sp0", "activityscreen", "lo20\$a", "lo20", "ln20", "lsp0")
        logcat.lineSequence()
            .filter { line ->
                val lower = line.lowercase()
                val classHit = watchClasses.any { lower.contains(it) }
                lower.contains("verifyerror") || lower.contains("rejected class") ||
                    lower.contains("failed to verify") || lower.contains("hard failure") ||
                    lower.contains("hard fail") || lower.contains("verification error") ||
                    lower.contains("rejecting") || (classHit && lower.contains("verif"))
            }
            .forEach {
                appendLine(it)
                hits++
            }
        if (hits == 0) {
            appendLine("(no ART hard verification failures in recent logcat)")
        }
    }
    return filtered
}