/*
 * Copyright 2026 Morphe.
 *
 * Executes page scripts via Chromium's {@link RenderFrameHost#d} path with a non-null
 * {@link org.chromium.base.Callback}. Passing null crashes JNI in {@code M5dgGNo$}.
 */

package app.morphe.extension.opera.patches;

import org.chromium.base.Callback;
import org.chromium.content_public.browser.RenderFrameHost;
import org.chromium.content_public.browser.WebContents;
import org.chromium.url.Origin;

import app.morphe.extension.shared.Logger;

public final class OperaPlaybackSpeedScriptInjector {
    private static final Callback<String> NO_OP_CALLBACK = new Callback<String>() {
        @Override
        public void onResult(String result) {
            // ignored
        }
    };

    private OperaPlaybackSpeedScriptInjector() {
    }

    /**
     * Runs {@code script} on the tab's primary {@link RenderFrameHost} after load completes.
     */
    public static void evaluate(WebContents webContents, String script) {
        if (webContents == null || script == null || script.isEmpty()) {
            return;
        }
        try {
            if (webContents.isDestroyed()) {
                return;
            }
            RenderFrameHost frame = webContents.F();
            if (frame == null) {
                return;
            }
            Origin origin = frame.c();
            frame.d(script, origin, NO_OP_CALLBACK);
        } catch (Throwable throwable) {
            Logger.printException(() -> "Opera playback speed script evaluation failed", throwable);
        }
    }
}