/*
 * Copyright 2026 Morphe.
 *
 * FTL-Patches pattern (BlazeFTL/FTL-Patches ModSettingsRow.java): custom layout view class
 * declared in resource XML; tap opens the Morphe settings UI.
 */

package app.morphe.extension.opera.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OperaPlaybackSpeedSettingsRow extends LinearLayout {
    public OperaPlaybackSpeedSettingsRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        OperaPlaybackSpeedMediaSettingsHook.inflateDefaultPlaybackSpeedRow(this);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View labeled = findViewWithTag("morphe_opera_playback_speed_row");
                if (labeled instanceof TextView) {
                    OperaPlaybackSpeedMediaSettingsHook.showDefaultPlaybackSpeedPicker(
                            v.getContext(), (TextView) labeled);
                }
            }
        });
    }
}