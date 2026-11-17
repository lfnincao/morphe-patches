/*
 * Copyright 2026 Morphe.
 *
 * Guards Morphe extension initialization so Chromium sandbox / isolated processes never
 * call {@link app.morphe.extension.shared.Utils#setContext}, which touches SharedPreferences.
 */

package app.morphe.extension.opera;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import app.morphe.extension.shared.Utils;

public final class OperaProcessGuard {
    private OperaProcessGuard() {
    }

    /**
     * Initializes Morphe only in the main {@code com.opera.browser} process.
     * Sandboxed renderer processes inherit {@code OperaApplication#onCreate} but must not
     * touch Morphe settings or SharedPreferences.
     */
    public static void initMainProcessContext(Context applicationContext) {
        if (applicationContext == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Process.isIsolated()) {
            return;
        }
        String processName = resolveProcessName(applicationContext);
        if (processName == null || !"com.opera.browser".equals(processName)) {
            return;
        }
        Utils.setContext(applicationContext);
    }

    private static String resolveProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        return context.getPackageName();
    }
}