package app.morphe.patches.opera.misc.extension

import app.morphe.patches.all.misc.extension.sharedExtensionPatch

/**
 * Ships Opera + shared extension classes without hooking [Application.onCreate].
 * Playback-speed and other bytecode-only Opera patches use this instead of
 * [operaSharedExtensionPatch] so Chromium sandbox processes never run Morphe settings code.
 */
val operaExtensionDexPatch = sharedExtensionPatch(
    listOf("opera", "shared"),
)