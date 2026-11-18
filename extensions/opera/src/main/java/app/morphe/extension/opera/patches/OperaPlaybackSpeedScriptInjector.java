/*
 * Copyright 2026 Morphe.
 *
 * Uses stock {@link WebContents#R0} ({@code WebContentsImpl.e()} + native {@code b}
 * + {@code J.N.MseJ7A4a}) — the only public Java entry on Opera 103.3 for passing a
 * script string with the WebContents native pointer. Vogelkop-MorphePatches does not
 * ship a playback-speed patch; this hook follows their structural
 * {@code OperaWebContentsDelegate.loadingStateChanged} fingerprint pattern.
 *
 * Does not call raw {@code J.N.Mhlly_d7} (CaptureDevicesDialogDelegate only; wrong
 * pointer → SIGSEGV) or {@code RenderFrameHost#d} (WebAuth).
 */

package app.morphe.extension.opera.patches;

import org.chromium.content_public.browser.WebContents;

import app.morphe.extension.opera.settings.OperaPlaybackSpeedPreferences;
import app.morphe.extension.shared.Logger;

public final class OperaPlaybackSpeedScriptInjector {
    private static final String VERIFY_LOG_TAG = "MorpheOperaPlaybackSpeed";

    private OperaPlaybackSpeedScriptInjector() {
    }

    public static void evaluate(WebContents webContents, String script) {
        if (webContents == null || script == null || script.isEmpty()) {
            return;
        }
        if (OperaPlaybackSpeedPreferences.isScriptInjectionDisabled()) {
            return;
        }
        try {
            if (webContents.isDestroyed()) {
                return;
            }
            Logger.printInfo(() -> VERIFY_LOG_TAG + ": dispatching script (" + script.length() + " chars)");
            webContents.R0(script);
        } catch (Throwable throwable) {
            disableInjectionSafely();
            try {
                Logger.printException(() -> VERIFY_LOG_TAG + ": evaluate failed — kill-switch set", throwable);
            } catch (Throwable suppressed) {
                // JNI may rethrow through JniAndroid.handleException; do not propagate.
            }
        }
    }

    private static void disableInjectionSafely() {
        try {
            OperaPlaybackSpeedPreferences.setScriptInjectionDisabled(true);
        } catch (Throwable ignored) {
            // Best effort: never throw from injector after a failure.
        }
    }
}