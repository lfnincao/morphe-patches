/*
 * Ported from Vogelkop-MorphePatches (MIT) — app/template/patches/opera/analytics/DisableAnalyticsPatch.kt
 */

package app.morphe.patches.opera.analytics

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

@Suppress("unused")
val operaDisableAnalyticsPatch = patch(
    name = "Disable analytics & tracking",
    description = "Stops Firebase Analytics, Google Ads/Doubleclick/IMA and " +
        "Leanplum from collecting or sending usage data by rewriting their " +
        "endpoint constants to a dead host. Firebase infrastructure that " +
        "features depend on (push notifications, feature flags) is left intact.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        val deadHost = "0.0.0.0"

        val trackerEndpoints = listOf(
            "app-measurement.com",
            "googleadservices.com",
            "doubleclick.net",
            "pagead2.googlesyndication.com",
            "imasdk.googleapis.com",
            "api.leanplum.com",
            "google-analytics.com",
            "googletagmanager.com",
            "pangle.io",
            "tiktokpangle",
            "pglstatp",
            "analytics.tiktok.com",
            "ads.vungle.com",
            "appsflyer.com",
            "appsflyersdk.com",
            "crashlytics.com",
            "adjust.com",
            "kochava.com",
        )

        val rewrittenPerEndpoint = mutableMapOf<String, Int>()
        for (endpoint in trackerEndpoints) {
            var hits = 0
            string(endpoint, StringComparisonType.CONTAINS).matchAllMethodIndicesForEach(requireMatches = false) { index ->
                val instruction = getInstruction<ReferenceInstruction>(index)
                if (instruction.opcode != Opcode.CONST_STRING &&
                    instruction.opcode != Opcode.CONST_STRING_JUMBO
                ) {
                    return@matchAllMethodIndicesForEach
                }
                val original = (instruction.reference as StringReference).string
                if (!original.contains(endpoint)) return@matchAllMethodIndicesForEach

                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        register,
                        ImmutableStringReference(original.replace(endpoint, deadHost)),
                    ),
                )
                hits++
            }
            if (hits > 0) rewrittenPerEndpoint[endpoint] = (rewrittenPerEndpoint[endpoint] ?: 0) + hits
        }

        var fieldHits = 0
        val rewrittenFields = mutableListOf<String>()
        classDefForEach { classDef ->
            val hasTrackerField = classDef.fields.any { field ->
                (field.initialValue as? StringEncodedValue)?.value?.let { value ->
                    trackerEndpoints.any { value.contains(it) }
                } == true
            }
            if (!hasTrackerField) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            for (field in mutableClass.fields) {
                val initialValue = field.initialValue as? MutableStringEncodedValue ?: continue
                val original = initialValue.value
                var rewritten = original
                for (endpoint in trackerEndpoints) {
                    if (rewritten.contains(endpoint)) {
                        rewritten = rewritten.replace(endpoint, deadHost)
                    }
                }
                if (rewritten != original) {
                    initialValue.setValue(rewritten)
                    fieldHits++
                    rewrittenFields += "${classDef.type.substringBeforeLast(';')}->${field.name}"
                }
            }
        }

        val total = rewrittenPerEndpoint.values.sum() + fieldHits
        if (total == 0) {
            throw PatchException(
                "No analytics/tracker endpoint constants were found. " +
                    "Is this an unsupported Opera version?",
            )
        }

        println(
            "Opera analytics: neutralized $total endpoint constant(s): " +
                rewrittenPerEndpoint.entries.joinToString(", ") { "${it.key} (x${it.value})" } +
                if (fieldHits > 0) " + field(s) " + rewrittenFields.joinToString(", ") else "",
        )
    }
}