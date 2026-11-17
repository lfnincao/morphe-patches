/*
 * Copyright 2026 Morphe.
 */

package app.morphe.extension.opera.settings;

import android.content.Context;
import android.content.SharedPreferences;

import app.morphe.extension.opera.OperaProcessGuard;

public final class OperaPlaybackSpeedPreferences {
    public static final String PREFS_NAME = "morphe_opera_playback_speed";
    public static final String KEY_SPEED = "default_html5_playback_speed";
    private static final float MIN = 0.25f;
    private static final float MAX = 3f;

    private OperaPlaybackSpeedPreferences() {
    }

    public static float resolvePlaybackSpeed(float patchDefault) {
        Context context = OperaProcessGuard.getApplicationContext();
        if (context == null) {
            return clamp(patchDefault);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_SPEED)) {
            float initial = clamp(patchDefault);
            prefs.edit().putFloat(KEY_SPEED, initial).apply();
            return initial;
        }
        return clamp(prefs.getFloat(KEY_SPEED, patchDefault));
    }

    public static void setPlaybackSpeed(float rate) {
        Context context = OperaProcessGuard.getApplicationContext();
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_SPEED, clamp(rate))
                .apply();
    }

    public static float getStoredSpeed(float fallback) {
        Context context = OperaProcessGuard.getApplicationContext();
        if (context == null) {
            return clamp(fallback);
        }
        return clamp(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_SPEED, fallback));
    }

    public static float clamp(float rate) {
        if (Float.isNaN(rate) || Float.isInfinite(rate) || rate <= 0f) {
            return 1f;
        }
        return Math.min(MAX, Math.max(MIN, rate));
    }

    public static String formatSpeedLabel(float rate) {
        if (rate == (int) rate) {
            return ((int) rate) + "x";
        }
        return rate + "x";
    }
}