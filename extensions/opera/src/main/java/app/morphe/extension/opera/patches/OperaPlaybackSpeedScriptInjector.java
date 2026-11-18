/*
 * Copyright 2026 Morphe.
 *
 * Injects page script via stock {@code WebContentsImpl} prologue ({@code e()} + {@code b:J})
 * and {@code J.N.MevqfbP8} — the only JNI in Opera 103.3 arm64 that passes a script string
 * with that pointer source. {@code J.N.Mhlly_d7} is for {@code CaptureDevicesDialogDelegate}
 * only and must not be used with {@code WebContentsImpl.b} (native SIGSEGV).
 */

package app.morphe.extension.opera.patches;

import org.chromium.content.browser.webcontents.WebContentsImpl;
import org.chromium.content_public.browser.WebContents;

import J.N;
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
            if (!(webContents instanceof WebContentsImpl)) {
                Logger.printInfo(() -> VERIFY_LOG_TAG + ": WebContents is not WebContentsImpl");
                disableInjectionSafely();
                return;
            }
            WebContentsImpl impl = (WebContentsImpl) webContents;
            impl.e();
            long nativePtr = impl.getNativePointer();
            if (nativePtr == 0L) {
                Logger.printInfo(() -> VERIFY_LOG_TAG + ": native WebContents pointer is zero");
                disableInjectionSafely();
                return;
            }
            N.MevqfbP8(nativePtr, script);
            Logger.printInfo(() -> VERIFY_LOG_TAG + ": script dispatched (" + script.length() + " chars)");
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