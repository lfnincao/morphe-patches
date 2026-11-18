package app.morphe.patches.mxplayer.ad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/** Force local ad pool factory to return the built-in no-op [Lm39;]. */
internal object LocalAdFactoryFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ad/local/ad/a;",
    name = "f",
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Lm39;",
    filters = listOf(
        string("OTT"),
    ),
)

/** Skip interstitial activity UI ([com.mxplay.monetize.MxInterstitialAdActivity]). */
internal object MxInterstitialAdActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/mxplay/monetize/MxInterstitialAdActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        opcode(Opcode.INVOKE_SUPER),
        fieldAccess(
            name = "activity_native_full_screen_ad",
            type = "I",
            opcode = Opcode.SGET,
        ),
        opcode(Opcode.INVOKE_VIRTUAL),
    ),
)

internal object BannerViewOnAttachedFingerprint : Fingerprint(
    definingClass = "Lcom/mxplay/monetize/v2/banner/BannerView;",
    name = "onAttachedToWindow",
    returnType = "V",
    filters = listOf(opcode(Opcode.INVOKE_SUPER)),
)

/** Stops banner refresh / AdView child wiring. */
internal object BannerViewShowFingerprint : Fingerprint(
    definingClass = "Lcom/mxplay/monetize/v2/banner/BannerView;",
    name = "a",
    returnType = "V",
    filters = listOf(
        opcode(Opcode.INVOKE_STATIC),
        opcode(Opcode.IPUT_BOOLEAN),
    ),
)

/** Folder list between-tray native ad row binder. */
internal object BetweenTrayNativeAdBinderP32Fingerprint : Fingerprint(
    definingClass = "Lp32;",
    name = "i",
    returnType = "V",
    parameters = listOf(
        "Lp32\$a;",
        "Lcom/mxtech/videoplayer/ad/online/ad/views/model/BetweenTrayItemAdResource;",
    ),
)

/** Alternate list binder for between-tray native ads. */
internal object BetweenTrayNativeAdBinderThiFingerprint : Fingerprint(
    definingClass = "Lthi;",
    name = "i",
    returnType = "V",
    parameters = listOf(
        "Lthi\$a;",
        "Lcom/mxtech/videoplayer/ad/online/ad/views/model/BetweenTrayItemAdResource;",
    ),
)

/** Loads GlobalNativeAds placement on folder screens. */
internal object GlobalNativeAdsLoadFingerprint : Fingerprint(
    definingClass = "Lxe8;",
    name = "h",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            name = "GlobalNativeAds",
            type = "Lcom/mxtech/videoplayer/ad/local/ad/AdPlacement;",
            definingClass = "Lcom/mxtech/videoplayer/ad/local/ad/AdPlacement;",
            opcode = Opcode.SGET_OBJECT,
        ),
    ),
)