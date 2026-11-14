/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.video.playerresponse.playerResponseRawBytesHookSmali
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerResumePositionPlayerResponseHookSmaliTest {
    @Test
    fun `copy register hook compiles on player parameter builder ABI`() {
        val method = playerParameterBuilderMethod()
        val hook = playerResponseRawBytesHookSmali(
            registerRawResponse = "v4",
            registerVideoId = "v0",
            registerIsShortAndOpeningOrPlaying = "v3",
            methodDescriptor = "Lapp/morphe/extension/youtube/patches/playback/ServerResumePositionPatch;->" +
                "onPlayerResponseReceived([BLjava/lang/String;Z)V",
        )
        method.addInstruction(0, hook)
        assertEquals(1, method.implementation!!.instructions.size)
    }

    @Test
    fun `legacy p13 invoke does not compile on player parameter builder ABI`() {
        val method = playerParameterBuilderMethod()
        val legacyHook =
            "invoke-static { p2, p1, p13 }, Lapp/morphe/extension/youtube/patches/playback/" +
                "ServerResumePositionPatch;->onPlayerResponseReceived([BLjava/lang/String;Z)V"
        assertFailsWith<PatchException> {
            method.addInstruction(0, legacyHook)
        }
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