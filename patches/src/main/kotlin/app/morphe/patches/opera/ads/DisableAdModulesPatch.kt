/*
 * Ported from Vogelkop-MorphePatches (MIT) — app/template/patches/opera/ads/DisableAdModulesPatch.kt
 */

package app.morphe.patches.opera.ads

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

@Suppress("unused")
val operaDisableAdModulesPatch = patch(
    name = "Disable ad modules",
    description = "Prevents Opera from loading its bundled third-party ad network " +
        "modules (Liftoff/Vungle, Mintegral, Pangle/ByteDance) by breaking the " +
        "reflective class lookup used by the ad mediation layer.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        val deadModule = "com.opera.android.ads.disabled.DisabledByMorphe"
        val adModuleClasses = listOf(
            "com.opera.android.ads.liftoff.LiftoffAdsModuleImpl",
            "com.opera.android.ads.mintegral.MintegralAdsModuleImpl",
            "com.opera.android.ads.pangle.PangleAdsModuleImpl",
        )

        var patched = 0
        for (adModuleClass in adModuleClasses) {
            string(adModuleClass).matchAllMethodIndicesForEach(requireMatches = false) { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        register,
                        ImmutableStringReference(deadModule),
                    ),
                )
                patched++
            }
        }

        if (patched != adModuleClasses.size) {
            throw PatchException(
                "Expected exactly ${adModuleClasses.size} ad module class references, " +
                    "found $patched. Is this an unsupported Opera version?",
            )
        }

        println("Opera ad modules: neutralized $patched module reference(s).")
    }
}