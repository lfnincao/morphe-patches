/*
 * Copyright 2026 Morphe.
 *
 * Page JS via stock frame evaluateJavaScript (not {@code MevqfbP8}, which only runs
 * in the selection "process text" path and does not execute in normal page load):
 *
 * {@code webContents.F()} → {@code RenderFrameHost.d(script, origin, callback)} → {@code M5dgGNo$}.
 */

package org.chromium.content.browser.webcontents;

import org.chromium.base.Callback;
import org.chromium.content_public.browser.RenderFrameHost;
import org.chromium.content_public.browser.WebContents;
import org.chromium.url.Origin;

public final class MorpheOperaPageScriptEvaluator {
    /** Must stay in dex verify — device logcat sentinel. */
    public static final String SENTINEL_LOG_PREFIX = "MORPHE_SPEED_INJECT";

    private MorpheOperaPageScriptEvaluator() {
    }

    public static boolean evaluateScript(WebContents webContents, String script) {
        if (webContents == null || script == null || script.isEmpty()) {
            return false;
        }
        if (webContents.isDestroyed()) {
            return false;
        }
        RenderFrameHost frame = webContents.F();
        if (frame == null) {
            return false;
        }
        Origin origin = frame.c();
        Callback<String> callback = result -> {
            // Stock frame eval always supplies a callback at JNI boundary.
        };
        try {
            frame.d(script, origin, callback);
            return true;
        } catch (Throwable first) {
            try {
                frame.a(script, origin, false, callback);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}