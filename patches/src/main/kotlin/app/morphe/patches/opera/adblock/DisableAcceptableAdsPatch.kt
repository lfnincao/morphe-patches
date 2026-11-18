/*
 * Ported from Vogelkop-MorphePatches (MIT) — app/template/patches/opera/adblock/DisableAcceptableAdsPatch.kt
 */

package app.morphe.patches.opera.adblock

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Suppress("unused")
val operaDisableAcceptableAdsPatch = resourcePatch(
    name = "Disable Acceptable Ads whitelist",
    description = "Replaces Opera's hidden partner whitelists (Google/Yandex " +
        "measurement, Taboola, gambling and shopping affiliates, AppsFlyer/Adjust/" +
        "Kochava/CJ tracking domains) with empty filter lists, so the built-in ad " +
        "blocker actually blocks them.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        val abpDirectory = get("assets/abp_files/op")
        if (!abpDirectory.isDirectory) {
            throw PatchException(
                "Decoded APK does not contain assets/abp_files/op/. " +
                    "Is this an unsupported Opera version?",
            )
        }

        var neutralized = 0
        val neutralizedNames = mutableListOf<String>()

        for (file in abpDirectory.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }) {
            val raw = file.readBytes()
            val text = decompress(raw) ?: continue

            val hasAllowDirectives =
                text.contains("DOCUMENT =") ||
                    text.contains("WHITE.PLAIN") ||
                    text.contains("WHITE.GLOB")
            val hasNoBlockingRules = !text.contains("BLACK")

            if (!hasAllowDirectives || !hasNoBlockingRules) continue

            file.outputStream().use { output ->
                GZIPOutputStream(output).use { gzip ->
                    gzip.write(EMPTY_COMPILED_LIST.toByteArray(Charsets.UTF_8))
                }
            }
            neutralized++
            neutralizedNames += file.name.take(12) + "…"
        }

        if (neutralized == 0) {
            throw PatchException(
                "No pure-whitelist filter lists found in assets/abp_files/op/. " +
                    "Either the whitelists were already removed, or this Opera " +
                    "version uses a different layout.",
            )
        }

        println(
            "Opera Acceptable Ads: neutralized $neutralized whitelist list(s): " +
                neutralizedNames.joinToString(", "),
        )
    }
}

private fun decompress(raw: ByteArray): String? {
    val isGzip = raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
    return try {
        val bytes = if (isGzip) {
            GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
        } else {
            raw
        }
        String(bytes, Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

private val EMPTY_COMPILED_LIST =
    """
    [default]
    BLACK = ([])
    DOMAINLESS = (["NO_ELEMENT":([]),])
    EXTENDED_BLACK = ([])
    EXTENDED_WHITE = ([])
    OFA.VERSION = 2
    WHITE = ([])
    """.trimIndent() + "\n"