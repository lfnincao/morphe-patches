/*
 * Adapted from Vogelkop-MorphePatches (https://github.com/maniesh-NR/Vogelkop-MorphePatches)
 * IgnoreBrowserBootFailuresPatch.kt.
 */

package app.morphe.patches.opera.startup

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.string
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private object MainLibraryBootFailureAnnouncerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I", "Ljava/lang/Throwable;"),
    filters = listOf(
        string("MainLibraryBoot: "),
    ),
)

private fun isAppInterfaceCall(reference: MethodReference): Boolean {
    if (reference.returnType != "V") return false
    if (reference.parameterTypes.size != 1 || reference.parameterTypes[0] != "I") return false
    if (reference.name == "onSuccess") return false
    val defining = reference.definingClass
    if (!defining.matches(Regex("^L[a-z0-9]{1,4};$"))) return false
    return true
}

@Suppress("unused")
val operaIgnoreBootFailuresPatch = bytecodePatch(
    name = "Ignore browser boot failures",
    description = "Rewrites MainLibraryBoot failure announcements into success so the welcome flow " +
        "can continue when Chromium boot fails (e.g. ARM build on x86 emulator). Fingerprints from " +
        "Vogelkop-MorphePatches.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        val coordinator = MainLibraryBootFailureAnnouncerFingerprint.originalClassDef
        val mutableClass = mutableClassDefBy(coordinator)

        val callbackInterface = coordinator.methods
            .firstOrNull { method ->
                method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == "Landroid/content/Context;" &&
                    (method.parameterTypes[1] as String).matches(Regex("^L[a-z0-9]{1,4};$"))
            }
            ?.parameterTypes?.get(1) as? String
            ?: throw PatchException("Subscriber registration method not found in the MainLibraryBoot coordinator")

        var rewritten = 0
        val seenCallSites = mutableListOf<String>()

        for (mutableMethod in mutableClass.methods) {
            val implementation = mutableMethod.implementation ?: continue
            val instructions = implementation.instructions.toList()
            instructions.forEachIndexed { index, ins ->
                if (ins !is ReferenceInstruction) return@forEachIndexed
                val reference = ins.reference as? MethodReference ?: return@forEachIndexed
                if (reference.definingClass != callbackInterface) return@forEachIndexed
                if (!isAppInterfaceCall(reference)) return@forEachIndexed

                val format = ins as? Instruction35c
                    ?: throw PatchException(
                        "Unexpected announce call format at ${mutableMethod.name}#$index",
                    )
                val receiver = format.registerC
                mutableMethod.replaceInstruction(
                    index,
                    "invoke-interface {v$receiver}, $callbackInterface->onSuccess()V",
                )
                rewritten++
                seenCallSites += mutableMethod.name
            }
        }

        if (rewritten != 2) {
            throw PatchException(
                "Expected exactly 2 boot failure announce calls in MainLibraryBoot coordinator, found $rewritten: $seenCallSites",
            )
        }

        println(
            "Opera boot failures: rewrote $rewritten failure announce call(s) on " +
                "$callbackInterface to success notifications ($seenCallSites)",
        )
    }
}