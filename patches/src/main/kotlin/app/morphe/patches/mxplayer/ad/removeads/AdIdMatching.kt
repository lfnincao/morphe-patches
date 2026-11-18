// Ad layout/id heuristics ported from BlazeFTL/FTL-Patches (RemoveAds).
// https://github.com/BlazeFTL/FTL-Patches
package app.morphe.patches.mxplayer.ad.removeads

private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
private val AD_WORDS = setOf("ad", "ads", "banner", "nativead")
private val AD_BLOCK_WORDS = setOf("block", "blocker", "blocking")

internal fun idLooksAdRelated(id: String): Boolean {
    val tokens = id.split('_', '-').flatMap { it.split(CAMEL_BOUNDARY) }.map { it.lowercase() }
    if (tokens.any { it in AD_BLOCK_WORDS }) return false
    return tokens.any { it in AD_WORDS }
}