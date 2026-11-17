/*
 * Copyright 2026 Morphe.
 *
 * Adds a Morphe "Default playback speed" row to Opera's Media settings screen.
 */

package app.morphe.extension.opera.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.opera.patches.UniversalVideoPlaybackSpeedPatch;

public final class OperaPlaybackSpeedMediaSettingsHook {
    private static final float[] SPEED_OPTIONS = {
            0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f,
    };

    private OperaPlaybackSpeedMediaSettingsHook() {
    }

    public static void onMediaSettingsScreen(ViewGroup container) {
        if (container == null || container.getChildCount() == 0) {
            return;
        }
        View content = container.getChildAt(container.getChildCount() - 1);
        if (!(content instanceof LinearLayout)) {
            return;
        }
        LinearLayout list = (LinearLayout) content;
        if (list.findViewWithTag("morphe_opera_playback_speed_row") != null) {
            return;
        }
        Context context = list.getContext();
        TextView row = buildRow(context, list);
        list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static TextView buildRow(Context context, LinearLayout list) {
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
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue out = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            row.setBackgroundResource(out.resourceId);
        }
        row.setOnClickListener(v -> showSpeedPicker(context, list, row));
        return row;
    }

    private static void showSpeedPicker(Context context, LinearLayout list, TextView row) {
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