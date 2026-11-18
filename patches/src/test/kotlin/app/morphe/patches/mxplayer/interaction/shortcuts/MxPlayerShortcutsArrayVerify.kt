package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_LIST_INDEX
import java.io.File
import java.util.regex.Pattern

private const val STOCK_SHORTCUT_COUNT = 12
private const val MENU_DELETE_RES = "0x7f120475"
private const val SCREEN_ROTATION_RES = "0x7f1206a5"
private val ARRAY_BAG_HEADER =
    Pattern.compile(
        """array/list_shortcuts: <bag>\s+Parent=[^\n]*Count=(\d+)""",
        Pattern.CASE_INSENSITIVE,
    )

/**
 * Ensures [list_shortcuts] grew by exactly one item and original entries kept distinct labels.
 */
internal fun verifyMxPlayerShortcutsArray(apkFile: File, stockApk: File? = null): String {
    val patched = dumpListShortcutsBag(apkFile)
    val stock =
        stockApk?.takeIf { it.isFile }?.let { dumpListShortcutsBag(it) }
            ?: patched.copy(count = STOCK_SHORTCUT_COUNT)

    var lines = buildString {
        appendLine("=== list_shortcuts verify ===")
        appendLine("Stock count: ${stock.count} (expected $STOCK_SHORTCUT_COUNT)")
        appendLine("Patched count: ${patched.count} (expected ${STOCK_SHORTCUT_COUNT + 1})")
        appendLine("Item refs: ${patched.references.joinToString { it }}")

        val countDelta = patched.count - stock.count
        appendLine(if (countDelta == 1) "OK  count increased by 1" else "MISSING  count delta=$countDelta (expected 1)")

        val rotOk = patched.references.getOrNull(0) == SCREEN_ROTATION_RES
        appendLine(
            if (rotOk) "OK  index 0 still $SCREEN_ROTATION_RES (screen_rotation)"
            else "MISSING  index 0 is ${patched.references.firstOrNull()} (expected $SCREEN_ROTATION_RES)",
        )

        val deleteRefs = patched.references.count { it == MENU_DELETE_RES }
        appendLine(
            if (deleteRefs == 1) "OK  exactly one $MENU_DELETE_RES (menu_delete)"
            else "MISSING  menu_delete refs=$deleteRefs",
        )

        val lastIsDelete = patched.references.lastOrNull() == MENU_DELETE_RES
        appendLine(
            if (lastIsDelete) "OK  index $DELETE_SHORTCUT_LIST_INDEX is menu_delete"
            else "MISSING  last ref is ${patched.references.lastOrNull()}",
        )

        if (patched.references.size >= 2) {
            val distinctHead = patched.references.take(DELETE_SHORTCUT_LIST_INDEX).distinct().size
            val headOk = distinctHead == DELETE_SHORTCUT_LIST_INDEX
            appendLine(
                if (headOk) "OK  indices 0..${DELETE_SHORTCUT_LIST_INDEX - 1} use $DELETE_SHORTCUT_LIST_INDEX distinct string refs"
                else "MISSING  only $distinctHead distinct refs in first $DELETE_SHORTCUT_LIST_INDEX rows",
            )
        }
    }

    val failed = lines.lines().any { it.startsWith("MISSING  ") }
    lines += if (failed) "FAILED\n" else "PASSED\n"

    require(lines.contains("PASSED")) { "list_shortcuts verification failed:\n$lines" }
    return lines
}

private data class ListShortcutsBag(val count: Int, val references: List<String>)

private fun dumpListShortcutsBag(apk: File): ListShortcutsBag {
    val aapt = resolveAapt()
    val out =
        ProcessBuilder(aapt, "dump", "--values", "resources", apk.absolutePath)
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
    val matcher = ARRAY_BAG_HEADER.matcher(out)
    check(matcher.find()) { "list_shortcuts bag not found in $apk" }
    val count = matcher.group(1)!!.toInt()
    val tail = out.substring(matcher.end())
    val end = tail.indexOf("resource 0x").takeIf { it > 0 } ?: tail.length
    val body = tail.substring(0, end)
    val refs =
        Regex("""\(reference\) (0x[0-9a-f]+)""", RegexOption.IGNORE_CASE)
            .findAll(body)
            .map { it.groupValues[1].lowercase() }
            .toList()
    check(refs.size == count) { "Expected $count list_shortcuts refs, parsed ${refs.size}" }
    return ListShortcutsBag(count, refs)
}

private fun resolveAapt(): String {
    val sdk = resolveAndroidSdk()
    val aapt = File(sdk, "build-tools/35.0.0/aapt")
    require(aapt.isFile) { "aapt not found: $aapt" }
    return aapt.absolutePath
}