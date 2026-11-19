/*
 * Copyright 2026 Morphe.
 *
 * Injects a document script on each completed page load so HTML5 {@code <video>}
 * and {@code <audio>} elements use a user-selected playback rate.
 *
 * Hook: {@code OperaWebContentsDelegate#loadingStateChanged} (Vogelkop fingerprint).
 * Eval: {@code WebContents.F()} → {@code RenderFrameHost.d(script, origin, callback)}.
 */

package app.morphe.extension.opera.patches;

import com.opera.android.browser.chromium.OperaWebContentsDelegate;

import app.morphe.extension.opera.settings.OperaPlaybackSpeedPreferences;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public class UniversalVideoPlaybackSpeedPatch {
    /** Embedded in injected JS for logcat / DOM verification on device. */
    public static final String SENTINEL_LOG_TOKEN = "MORPHE_SPEED_INJECT";

    @SuppressWarnings("SameReturnValue")
    public static boolean isPatchIncluded() {
        return false;
    }

    public static float getPatchDefaultPlaybackSpeed() {
        return 1.0f;
    }

    public static float getPlaybackSpeed() {
        return OperaPlaybackSpeedPreferences.resolvePlaybackSpeed(getPatchDefaultPlaybackSpeed());
    }

    public static void onLoadingStateChanged(Object delegate, boolean isLoading) {
        if (!isPatchIncluded() || isLoading || delegate == null) {
            return;
        }
        try {
            float rate = getPlaybackSpeed();
            OperaWebContentsDelegate.patch_executePlaybackSpeedScript(
                    (OperaWebContentsDelegate) delegate, buildInjectionScript(rate));
            OperaPlaybackSpeedScriptInjector.scheduleLoadCompleteRetries(rate);
            Logger.printInfo(() -> "Applied HTML5 playback speed script (rate=" + rate + ")");
        } catch (Exception ex) {
            Logger.printException(() -> "onLoadingStateChanged failure", ex);
        }
    }

    public static void onDefaultSpeedChangedFromSettings(float rate) {
        if (!isPatchIncluded()) {
            return;
        }
        OperaPlaybackSpeedScriptInjector.reevaluateActiveTab(rate);
    }

    public static String buildInjectionScript(float rate) {
        if (rate <= 0f || Float.isNaN(rate) || Float.isInfinite(rate)) {
            rate = 1.0f;
        }
        return "(function(){"
                + "var r="
                + rate
                + ";"
                + "try{"
                + "var _v=document.querySelectorAll('video').length;"
                + "console.log('"
                + SENTINEL_LOG_TOKEN
                + " v='+r+' videos='+_v);"
                + "if(document.documentElement){"
                + "document.documentElement.setAttribute('data-morphe-speed',String(r));"
                + "}"
                + "}catch(_s){}"
                + "function applyRate(el){"
                + "try{"
                + "if(!el||typeof el.playbackRate!=='number')return;"
                + "if(el.playbackRate!==r)el.playbackRate=r;"
                + "}catch(x){}"
                + "}"
                + "function applyAll(){"
                + "try{"
                + "var m=document.querySelectorAll('video,audio');"
                + "for(var i=0;i<m.length;i++)applyRate(m[i]);"
                + "}catch(e){}"
                + "}"
                + "window.__morpheSetPlaybackSpeed=function(n){"
                + "if(typeof n!=='number'||!(n>0))return;"
                + "r=n;window.__morphePlaybackSpeed=r;"
                + "try{document.documentElement.setAttribute('data-morphe-speed',String(r));}catch(e){}"
                + "applyAll();"
                + "};"
                + "window.__morphePlaybackSpeed=r;"
                + "if(window.__morphePlaybackSpeedBooted){"
                + "window.__morpheSetPlaybackSpeed(r);"
                + "return;"
                + "}"
                + "window.__morphePlaybackSpeedBooted=true;"
                + "applyAll();"
                + "try{"
                + "new MutationObserver(function(ms){"
                + "for(var i=0;i<ms.length;i++){"
                + "var ns=ms[i].addedNodes;"
                + "for(var j=0;j<ns.length;j++){"
                + "var n=ns[j];"
                + "if(n.nodeType!==1)continue;"
                + "var t=n.nodeName;"
                + "if(t==='VIDEO'||t==='AUDIO')applyRate(n);"
                + "else if(n.querySelectorAll){"
                + "var q=n.querySelectorAll('video,audio');"
                + "for(var k=0;k<q.length;k++)applyRate(q[k]);"
                + "}"
                + "}"
                + "}"
                + "}).observe(document.documentElement||document,{childList:true,subtree:true});"
                + "var ev=['play','loadeddata','loadedmetadata','ratechange','emptied','canplay','durationchange'];"
                + "for(var x=0;x<ev.length;x++){"
                + "document.addEventListener(ev[x],function(e){applyRate(e.target);},true);"
                + "}"
                + "}catch(e){}"
                + "})();";
    }
}