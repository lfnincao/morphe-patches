/*
 * Copyright 2026 Morphe.
 *
 * Injects document scripts via Chromium {@code WebContentsAndroid::EvaluateJavaScript}
 * ({@code J.N.Mhlly_d7}), not {@link org.chromium.content_public.browser.WebContents#R0}
 * ({@code addMessageToDevToolsConsole} / {@code MseJ7A4a}) and not
 * {@link org.chromium.content_public.browser.RenderFrameHost#d} (WebAuth).
 */

package app.morphe.extension.opera.patches;

import java.lang.reflect.Field;

import org.chromium.content_public.browser.JavaScriptCallback;
import org.chromium.content_public.browser.WebContents;

import J.N;
import app.morphe.extension.shared.Logger;

public final class OperaPlaybackSpeedScriptInjector {
    private static final String VERIFY_LOG_TAG = "MorpheOperaPlaybackSpeed";
    private static final String NATIVE_PTR_FIELD = "b";

    private OperaPlaybackSpeedScriptInjector() {
    }

    /**
     * Runs {@code script} on the tab's {@link WebContents} after load completes.
     */
    public static void evaluate(WebContents webContents, String script) {
        if (webContents == null || script == null || script.isEmpty()) {
            return;
        }
        try {
            if (webContents.isDestroyed()) {
                return;
            }
            long nativePtr = nativeWebContentsPointer(webContents);
            if (nativePtr == 0L) {
                Logger.printInfo(() -> VERIFY_LOG_TAG + ": missing native WebContents pointer");
                return;
            }
            Logger.printInfo(() -> VERIFY_LOG_TAG + ": evaluating playback speed script (" + script.length() + " chars)");
            JavaScriptCallback callback = () -> Logger.printInfo(() -> VERIFY_LOG_TAG + ": JS evaluate callback");
            N.Mhlly_d7(nativePtr, script, callback, false);
        } catch (Throwable throwable) {
            try {
                Logger.printException(() -> VERIFY_LOG_TAG + ": evaluate failed", throwable);
            } catch (Throwable suppressed) {
                // JNI may rethrow through JniAndroid.handleException; do not propagate.
            }
        }
    }

    private static long nativeWebContentsPointer(WebContents webContents) throws ReflectiveOperationException {
        Field field = webContents.getClass().getDeclaredField(NATIVE_PTR_FIELD);
        field.setAccessible(true);
        return field.getLong(webContents);
    }
}