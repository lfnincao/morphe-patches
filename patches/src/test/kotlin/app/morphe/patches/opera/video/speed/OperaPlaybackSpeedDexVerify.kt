/*
 * Verifies patched Opera DEX for UniversalVideoPlaybackSpeedPatch.getPlaybackSpeed().
 *
 * Usage: OperaPlaybackSpeedDexVerifyKt <patched.apk>
 */
package app.morphe.patches.opera.video.speed

import app.morphe.patcher.dex.SdkDexVerifier
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.Opcode
import java.io.File
import java.util.zip.ZipFile

private const val EXTENSION_TYPE =
    "Lapp/morphe/extension/opera/patches/UniversalVideoPlaybackSpeedPatch;"
private const val INJECTOR_TYPE =
    "Lapp/morphe/extension/opera/patches/OperaPlaybackSpeedScriptInjector;"
private const val RENDER_FRAME_HOST_TYPE =
    "Lorg/chromium/content_public/browser/RenderFrameHost;"
private const val WEB_AUTH_RESULT_TYPE =
    "Lorg/chromium/content_public/browser/RenderFrameHost${'$'}WebAuthSecurityChecksResults;"
private const val CHROMIUM_JNI_CLASS = "LJ/N;"
private const val EVALUATE_JS_NATIVE = "Mhlly_d7"
private const val WEB_CONTENTS_TYPE = "Lorg/chromium/content_public/browser/WebContents;"
private const val OPERA_APPLICATION_TYPE = "Lcom/opera/android/OperaApplication;"
private const val SHARED_UTILS_TYPE = "Lapp/morphe/extension/shared/Utils;"
private const val OPERA_PROCESS_GUARD_TYPE = "Lapp/morphe/extension/opera/OperaProcessGuard;"
private const val PLAYBACK_PREFS_TYPE = "Lapp/morphe/extension/opera/settings/OperaPlaybackSpeedPreferences;"
private const val MEDIA_SETTINGS_ROW_TYPE =
    "Lapp/morphe/extension/opera/settings/OperaPlaybackSpeedSettingsRow;"

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: OperaPlaybackSpeedDexVerifyKt <patched.apk>" }
    val apk = File(args[0]).canonicalFile
    require(apk.isFile) { "Missing APK: $apk" }

    val dexFiles = mutableListOf<File>()
    val smaliOut = StringBuilder()
    var found = false
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bytes.inputStream())
                val classDef = dex.classes.firstOrNull { it.type == EXTENSION_TYPE } ?: return@forEach
                found = true
                verifyPatchDefaultSpeedConstant(classDef, smaliOut)
                verifyGetPlaybackSpeedUsesPrefs(classDef)
                val tmp = File.createTempFile("opera-verify-", ".dex")
                tmp.writeBytes(bytes)
                dexFiles += tmp
            }
    }
    check(found) { "Extension class not in patched APK" }

    verifyExtensionMethodExists(apk, INJECTOR_TYPE, "evaluate")
    verifyInjectorDoesNotCallWebAuth(apk)
    verifyInjectorUsesEvaluateJavaScript(apk)
    listOf(
        "patch_executePlaybackSpeedScript" to "Lcom/opera/android/browser/chromium/OperaWebContentsDelegate;",
    ).forEach { (name, type) ->
        verifyHelperMethod(apk, type, name, smaliOut)
    }

    verifyOperaApplicationUsesProcessGuard(apk)
    verifyMediaSettingsRowPresent(apk)

    val sdk = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME required")
    val verifier = SdkDexVerifier(File(sdk))
    dexFiles.forEach { verifier.verifyDexFile(it) }
    println("ART/SDK DEX verification passed for ${dexFiles.size} file(s) containing extension.")
    println("--- getPatchDefaultPlaybackSpeed() smali ---")
    print(smaliOut)
}

private fun verifyPatchDefaultSpeedConstant(
    classDef: com.android.tools.smali.dexlib2.iface.ClassDef,
    smaliOut: StringBuilder,
) {
    val method = classDef.methods.firstOrNull { it.name == "getPatchDefaultPlaybackSpeed" }
        ?: error("getPatchDefaultPlaybackSpeed not found in $EXTENSION_TYPE")
    val instructions = method.implementation?.instructions?.toList()
        ?: error("getPatchDefaultPlaybackSpeed has no implementation")
    check(instructions.size >= 2) { "getPatchDefaultPlaybackSpeed too short" }
    val first = instructions.first()
    val last = instructions.last()
    check(first.opcode == Opcode.CONST || first.opcode == Opcode.CONST_HIGH16) {
        "getPatchDefaultPlaybackSpeed must load a float constant"
    }
    check(last.opcode == Opcode.RETURN) { "getPatchDefaultPlaybackSpeed must return float" }
    smaliOut.appendLine("# $EXTENSION_TYPE->getPatchDefaultPlaybackSpeed()")
    instructions.forEach { ins -> smaliOut.appendLine(instructionToSmali(ins)) }
    smaliOut.appendLine()
}

private fun verifyGetPlaybackSpeedUsesPrefs(
    classDef: com.android.tools.smali.dexlib2.iface.ClassDef,
) {
    val method = classDef.methods.firstOrNull { it.name == "getPlaybackSpeed" }
        ?: error("getPlaybackSpeed not found")
    val callsPrefs = method.implementation?.instructions?.any { instruction ->
        val ref = instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
        val methodRef = ref?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
        methodRef?.definingClass == PLAYBACK_PREFS_TYPE
    } == true
    check(callsPrefs) {
        "getPlaybackSpeed must resolve stored speed via $PLAYBACK_PREFS_TYPE"
    }
}

private fun instructionToSmali(ins: Instruction): String {
    return when (ins.opcode) {
        Opcode.CONST, Opcode.CONST_HIGH16 -> {
            val reg = (ins as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
            val lit = (ins as com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction).narrowLiteral
            "${ins.opcode.name.lowercase().replace('_', '/')} v$reg, 0x${Integer.toHexString(lit)}"
        }
        Opcode.RETURN -> {
            val reg = (ins as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
            "return v$reg"
        }
        else -> ins.opcode.toString()
    }
}

private fun verifyOperaApplicationUsesProcessGuard(apk: File) {
    var sawGuard = false
    var sawDirectUtils = false
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val onCreate = dex.classes
                    .firstOrNull { it.type == OPERA_APPLICATION_TYPE }
                    ?.methods
                    ?.firstOrNull { it.name == "onCreate" && it.parameterTypes.isEmpty() }
                    ?: return@forEach
                val impl = onCreate.implementation ?: return@forEach
                impl.instructions.forEach { instruction ->
                    val reference = instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
                    val methodRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                        ?: return@forEach
                    val owner = methodRef.definingClass
                    if (owner == OPERA_PROCESS_GUARD_TYPE && methodRef.name == "initMainProcessContext") {
                        sawGuard = true
                    }
                    if (owner == SHARED_UTILS_TYPE && methodRef.name == "setContext") {
                        sawDirectUtils = true
                    }
                }
            }
    }
    check(sawGuard) { "OperaApplication.onCreate must call OperaProcessGuard.initMainProcessContext" }
    check(!sawDirectUtils) { "OperaApplication.onCreate must not call Utils.setContext without ProcessGuard" }
    println("Static check passed: OperaApplication.onCreate uses OperaProcessGuard.")
}

private fun verifyMediaSettingsRowPresent(apk: File) {
    verifyExtensionMethodExists(apk, MEDIA_SETTINGS_ROW_TYPE, "<init>")
    val layoutHasRow = ZipFile(apk).use { zip ->
        val entry = zip.getEntry("res/layout/fragment_media_settings.xml") ?: return@use false
        val xml = zip.getInputStream(entry).readBytes().decodeToString()
        xml.contains(OPERA_PLAYBACK_SPEED_SETTINGS_ROW)
    }
    check(layoutHasRow) {
        "res/layout/fragment_media_settings.xml must declare $OPERA_PLAYBACK_SPEED_SETTINGS_ROW"
    }
    println("Static check passed: Media settings row (FTL-style layout) installed.")
}

private fun verifyInjectorDoesNotCallWebAuth(apk: File) {
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val injector = dex.classes.firstOrNull { it.type == INJECTOR_TYPE } ?: return@forEach
                injector.methods.forEach { method ->
                    method.implementation?.instructions?.forEach { instruction ->
                        val reference = instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
                        val methodRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                        val typeRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.TypeReference
                        if (methodRef != null) {
                            check(methodRef.definingClass != RENDER_FRAME_HOST_TYPE) {
                                "Injector must not call RenderFrameHost " +
                                    "(${methodRef.definingClass}->${methodRef.name})"
                            }
                        }
                        val touched = typeRef?.type ?: methodRef?.definingClass
                        check(touched != WEB_AUTH_RESULT_TYPE) {
                            "Injector references WebAuthSecurityChecksResults"
                        }
                    }
                }
            }
    }
    println("Static check passed: injector does not reference WebAuth RenderFrameHost APIs.")
}

private fun verifyInjectorUsesEvaluateJavaScript(apk: File) {
    var sawEvaluateNative = false
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val injector = dex.classes.firstOrNull { it.type == INJECTOR_TYPE } ?: return@forEach
                val evaluate = injector.methods.firstOrNull { it.name == "evaluate" }
                    ?: error("evaluate() missing from injector")
                evaluate.implementation?.instructions?.forEach { instruction ->
                    val reference = instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
                    val methodRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                    if (methodRef != null) {
                        check(methodRef.definingClass != WEB_CONTENTS_TYPE || methodRef.name != "R0") {
                            "Injector must not call WebContents.R0 (devtools console only)"
                        }
                        if (methodRef.definingClass == CHROMIUM_JNI_CLASS && methodRef.name == EVALUATE_JS_NATIVE) {
                            sawEvaluateNative = true
                        }
                    }
                }
            }
    }
    check(sawEvaluateNative) {
        "Injector must invoke $CHROMIUM_JNI_CLASS->$EVALUATE_JS_NATIVE (Chromium EvaluateJavaScript)"
    }
    println("Static check passed: injector calls $CHROMIUM_JNI_CLASS->$EVALUATE_JS_NATIVE.")
}

private fun verifyExtensionMethodExists(apk: File, type: String, name: String) {
    ZipFile(apk).use { zip ->
        val found = zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .any { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                dex.classes.any { classDef ->
                    classDef.type == type && classDef.methods.any { it.name == name }
                }
            }
        check(found) { "$type->$name missing from patched APK" }
    }
    println("Static check passed: $type->$name present in DEX.")
}

private fun verifyHelperMethod(apk: File, type: String, name: String, out: StringBuilder) {
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val classDef = dex.classes.firstOrNull { it.type == type } ?: return@forEach
                val method = classDef.methods.firstOrNull { it.name == name }
                    ?: error("$type->$name not found in patched APK")
                val instructions = method.implementation?.instructions?.toList()
                    ?: error("$type->$name has no implementation")
                check(instructions.isNotEmpty()) { "$type->$name is empty" }
                out.appendLine("# ${entry.name} :: $type->$name")
                instructions.forEach { ins -> out.appendLine("  $ins") }
                out.appendLine()
            }
    }
}