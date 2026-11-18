/*
 * Ported from Vogelkop-MorphePatches (MIT) — app/template/patches/opera/ads/RemoveSponsoredSpeedDialPatch.kt
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
val operaRemoveSponsoredSpeedDialPatch = patch(
    name = "Remove sponsored Speed Dial",
    description = "Stops sponsored/partner tiles from appearing on the start page " +
        "by making Opera's suggested-sites database queries return no rows. " +
        "Regular Speed Dial entries and bookmarks are not affected.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        val sqlReplacements = mapOf(
            "SELECT * FROM partner_data" to "SELECT * FROM partner_data WHERE 0",
            "SELECT * FROM partner_tree LIMIT 1" to "SELECT * FROM partner_tree WHERE 0 LIMIT 1",
            "SELECT * FROM domain_data" to "SELECT * FROM domain_data WHERE 0",
        )

        var patched = 0
        for ((originalSql, emptySql) in sqlReplacements) {
            string(originalSql).matchAllMethodIndicesForEach(requireMatches = false) { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        register,
                        ImmutableStringReference(emptySql),
                    ),
                )
                patched++
            }
        }

        if (patched != sqlReplacements.size) {
            throw PatchException(
                "Expected exactly ${sqlReplacements.size} suggested-sites SQL constants, " +
                    "found $patched. Is this an unsupported Opera version?",
            )
        }

        println("Opera sponsored Speed Dial: neutralized $patched SQL query(ies).")
    }
}