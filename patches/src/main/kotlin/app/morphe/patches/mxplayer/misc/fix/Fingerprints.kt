package app.morphe.patches.mxplayer.misc.fix

import app.morphe.patcher.Fingerprint

/** Native guard entry from [com.mxtech.videoplayer.pro.App.f]. */
internal object MxBhStartupDFingerprint : Fingerprint(
    definingClass = "Lcom/ddx/axx/axx/axx/BH;",
    name = "d",
    returnType = "V",
    parameters = listOf("Lcom/mxtech/videoplayer/pro/App;"),
)

/** Secondary native guard entry from [com.mxtech.videoplayer.pro.App.g] and elsewhere. */
internal object MxBhStartupBFingerprint : Fingerprint(
    definingClass = "Lcom/ddx/axx/axx/axx/BH;",
    name = "b",
    returnType = "V",
    parameters = listOf("LlV;", "I"),
)

/** Startup hook that calls [MxBhStartupDFingerprint] then native BH.w. */
internal object MxAppStartupFFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/pro/App;",
    name = "f",
    returnType = "V",
)

/** Secondary startup hook that calls [MxBhStartupBFingerprint] then native BH.w. */
internal object MxAppStartupGFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/pro/App;",
    name = "g",
    returnType = "V",
)