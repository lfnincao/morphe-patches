package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_FREE
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_PRO
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT_PRO
import app.morphe.patches.mxplayer.shared.Constants.FREE_DEFAULT_SHORTCUTS_MASK
import app.morphe.patches.mxplayer.shared.Constants.PACKAGE_NAME_FREE
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Fresh-install shortcut bitmask OR — adapted from BlazeFTL/FTL-Patches
 * [PlayerDefaultShortcutsPatch.kt](https://github.com/BlazeFTL/FTL-Patches).
 */
internal val playerDefaultShortcutsPatch = bytecodePatch(
    name = null,
    description = "Ensures delete shortcut bit is on for installs that never customized shortcuts.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO, COMPATIBILITY_MX_PLAYER_FREE)

    execute {
        val method = DefaultShortcutsFingerprint.method
        val matches = DefaultShortcutsFingerprint.instructionMatches

        val accumulatorRegister = matches[1].getInstruction<OneRegisterInstruction>().registerA
        val checkRegister = matches[4].getInstruction<OneRegisterInstruction>().registerA
        val ifEqzIndex = matches[5].index
        val ifEqzInstruction = method.implementation!!.instructions[ifEqzIndex]

        val injected = when (packageMetadata.packageName) {
            PACKAGE_NAME_FREE -> """
                if-nez v$checkRegister, :original_check
                const v$accumulatorRegister, $FREE_DEFAULT_SHORTCUTS_MASK
                return v$accumulatorRegister
            """.trimIndent()
            else -> {
                val defaultMask = (0x780F or DELETE_SHORTCUT_BIT_PRO) and 0xFF7F
                val maskHex = "0x%x".format(defaultMask)
                """
                if-nez v$checkRegister, :original_check
                const v$checkRegister, -0xff80
                and-int/2addr v$accumulatorRegister, v$checkRegister
                const v$checkRegister, $maskHex
                or-int/2addr v$accumulatorRegister, v$checkRegister
                return v$accumulatorRegister
                """.trimIndent()
            }
        }

        method.addInstructionsWithLabels(
            ifEqzIndex,
            injected,
            ExternalLabel("original_check", ifEqzInstruction),
        )
    }
}