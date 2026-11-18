/*
 * Copyright 2026 Morphe.
 *
 * Morphe "Default playback speed" UI (0.25x–3x, SharedPreferences). Row placement uses
 * FTL-Patches-style custom view in {@code fragment_media_settings.xml}; see
 * {@link OperaPlaybackSpeedSettingsRow}.
 */

package app.morphe.extension.opera.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.opera.patches.UniversalVideoPlaybackSpeedPatch;

public final class OperaPlaybackSpeedMediaSettingsHook {
    private static final float[] SPEED_OPTIONS = {
            0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f,
    };

    private OperaPlaybackSpeedMediaSettingsHook() {
    }

    /** Builds the row label inside {@link OperaPlaybackSpeedSettingsRow}. */
    public static void inflateDefaultPlaybackSpeedRow(LinearLayout parent) {
        if (parent == null || parent.getChildCount() > 0) {
            return;
        }
        Context context = parent.getContext();
        float patchDefault = UniversalVideoPlaybackSpeedPatch.getPatchDefaultPlaybackSpeed();
        float current = OperaPlaybackSpeedPreferences.getStoredSpeed(patchDefault);

        TextView row = new TextView(context);
        row.setTag("morphe_opera_playback_speed_row");
        int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f, context.getResources().getDisplayMetrics());
        row.setPadding(pad, pad, pad, pad);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        updateRowText(row, current);
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public static void showDefaultPlaybackSpeedPicker(Context context, TextView row) {
        if (context == null || row == null) {
            return;
        }
        float patchDefault = UniversalVideoPlaybackSpeedPatch.getPatchDefaultPlaybackSpeed();
        float current = OperaPlaybackSpeedPreferences.getStoredSpeed(patchDefault);
        String[] labels = new String[SPEED_OPTIONS.length];
        int checked = 0;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            labels[i] = OperaPlaybackSpeedPreferences.formatSpeedLabel(SPEED_OPTIONS[i]);
            if (Math.abs(SPEED_OPTIONS[i] - current) < 0.001f) {
                checked = i;
            }
        }
        new AlertDialog.Builder(context)
                .setTitle("Default playback speed")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    float chosen = SPEED_OPTIONS[which];
                    OperaPlaybackSpeedPreferences.setPlaybackSpeed(chosen);
                    updateRowText(row, chosen);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void updateRowText(TextView row, float rate) {
        row.setText("Default playback speed — "
                + OperaPlaybackSpeedPreferences.formatSpeedLabel(rate));
    }
}