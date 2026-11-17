/*
 * Copyright 2026 Morphe.
 *
 * Opera compatibility targets adapted from Vogelkop-MorphePatches
 * (https://github.com/maniesh-NR/Vogelkop-MorphePatches) OperaCompat.kt.
 */

package app.morphe.patches.opera.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// Version verified via APK decompilation (Opera 103.2.5234.90977 / 103.3.5234.91185 family).
private val OPERA_TARGETS = listOf(
    AppTarget(version = "103.3.5234.91185"),
    AppTarget(version = "103.2.5234.90977"),
    AppTarget(version = null, isExperimental = true),
)

/** Opera Browser — Play split bundle (.apkm). */
val COMPATIBILITY_OPERA = Compatibility(
    name = "Opera Browser",
    packageName = "com.opera.browser",
    apkFileType = ApkFileType.APKM,
    appIconColor = 0xFF1B36,
    targets = OPERA_TARGETS,
)

/** Opera Browser — direct-download generic APK from get.opera.com. */
val COMPATIBILITY_OPERA_DIRECT = Compatibility(
    name = "Opera Browser",
    packageName = "com.opera.browser",
    apkFileType = ApkFileType.APK,
    appIconColor = 0xFF1B36,
    targets = OPERA_TARGETS,
)