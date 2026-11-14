/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.toInstructions
import app.morphe.patches.youtube.video.playerresponse.playerResponseRawBytesHookSmali
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerResumePositionPlayerResponseHookSmaliTest {
    @Test
    fun `raw response hook compiles without touching p0 scratch alias`() {
        val method = playerParameterBuilderMethod()
        val hook = """
            move-object/from16 v2, p2
            invoke-static { v2, v0, v3 }, Lapp/morphe/extension/youtube/patches/playback/ServerResumePositionPatch;->onPlayerResponseReceived([BLjava/lang/String;Z)V
            move-object/from16 v2, p4
        """.trimIndent()
        method.addInstructions(0, hook)
        assertEquals(3, method.implementation!!.instructions.size)
    }

    @Test
    fun `copy register moves stay within v0 through v3 below p0`() {
        val moves = """
            move-object/from16 v0, p1
            move-object/from16 v1, p3
            move-object/from16 v2, p4
            move/from16        v3, p13
        """.trimIndent()
        assertEquals(4, moves.toInstructions(playerParameterBuilderMethod()).size)
    }

    @Test
    fun `helper smali for dedicated scratch slot compiles when p0 is above v4`() {
        val hook = playerResponseRawBytesHookSmali(
            registerRawResponse = "v4",
            registerVideoId = "v0",
            registerIsShortAndOpeningOrPlaying = "v3",
            methodDescriptor = "Lapp/morphe/extension/youtube/patches/playback/ServerResumePositionPatch;->" +
                "onPlayerResponseReceived([BLjava/lang/String;Z)V",
        )
        val method = playerParameterBuilderMethod()
        method.addInstruction(0, hook)
        assertEquals(1, method.implementation!!.instructions.size)
    }

    private fun playerParameterBuilderMethod() = ImmutableMethod(
        "Ldummy;",
        "buildPlayerParameters",
        listOf(
            ImmutableMethodParameter("Ljava/lang/String;", emptySet(), null),
            ImmutableMethodParameter("[B", emptySet(), null),
            ImmutableMethodParameter("Ljava/lang/String;", emptySet(), null),
            ImmutableMethodParameter("Ljava/lang/String;", emptySet(), null),
            ImmutableMethodParameter("I", emptySet(), null),
            ImmutableMethodParameter("Z", emptySet(), null),
            ImmutableMethodParameter("I", emptySet(), null),
            ImmutableMethodParameter("L", emptySet(), null),
            ImmutableMethodParameter("Ljava/util/Set;", emptySet(), null),
            ImmutableMethodParameter("Ljava/lang/String;", emptySet(), null),
            ImmutableMethodParameter("Ljava/lang/String;", emptySet(), null),
            ImmutableMethodParameter("L", emptySet(), null),
            ImmutableMethodParameter("Z", emptySet(), null),
            ImmutableMethodParameter("Z", emptySet(), null),
            ImmutableMethodParameter("Z", emptySet(), null),
            ImmutableMethodParameter("Lj$/time/Duration;", emptySet(), null),
        ),
        "L",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
        emptySet(),
        emptySet(),
        MutableMethodImplementation(32),
    ).toMutable()
}