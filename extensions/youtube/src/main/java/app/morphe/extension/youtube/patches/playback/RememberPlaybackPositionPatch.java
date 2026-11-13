/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Persists the last watched position for each non-Shorts video and seeks to it
 * when the video is re-opened. This fixes YouTube restarting videos from 00:00
 * when they are played from a playlist or autoplay queue, even though the client
 * already tracks watch progress for direct opens.
 */
@SuppressWarnings("unused")
public final class RememberPlaybackPositionPatch {

    /**
     * Minimum playback time before a position is worth saving or restoring.
     */
    private static final long MIN_PROGRESS_MS = 10_000L;

    /**
     * Do not save or restore near the end of a video (treat the video as finished).
     */
    private static final long END_PADDING_MS = 10_000L;

    /**
     * How often the playback position is saved while watching.
     */
    private static final long SAVE_INTERVAL_MS = 5_000L;

    /**
     * Ignore a save when the position has barely moved since the last write.
     */
    private static final long SAVE_DELTA_MS = 1_000L;

    /**
     * Give up trying to restore once the user has been watching for this long.
     */
    private static final long RESTORE_WINDOW_MS = 5_000L;

    /**
     * Delay between restore checks while the player is still loading.
     */
    private static final long RESTORE_POLL_DELAY_MS = 500L;

    /**
     * Maximum number of restore checks. Roughly 10 seconds for the player to become ready.
     */
    private static final int RESTORE_MAX_ATTEMPTS = 20;

    /**
     * Delay between seek attempts when restoring.
     */
    private static final long RESTORE_SEEK_RETRY_DELAY_MS = 1000L;

    /**
     * Maximum number of seek attempts. Early seeks are sometimes accepted while the
     * player is still loading, which leaves playback stuck on the spinner.
     */
    private static final int RESTORE_SEEK_MAX_ATTEMPTS = 10;

    /**
     * How close the current time must already be to the saved position to skip seeking.
     * YouTube restores this itself when a video is opened directly.
     */
    private static final long ALREADY_RESTORED_TOLERANCE_MS = 2_000L;

    /**
     * Maximum number of saved videos kept. Prevents unbounded growth of the settings storage.
     */
    private static final int MAX_SAVED_VIDEOS = 100;

    /**
     * Incremented on every new video, used to cancel pending restore checks.
     */
    private static long newVideoGeneration;

    private static int restoreAttempts;

    /**
     * True while a saved position is waiting to be restored for the current video.
     * While pending, saving is suppressed so the live playback position cannot
     * overwrite the position to restore.
     */
    private static boolean restorePending;

    /**
     * True while {@link #attemptRestoreSeek} is retrying seeks for the current video.
     */
    private static boolean restoreSeekInProgress;

    private static long lastSavedTime = -1L;

    private static long lastSaveAtMs;

    @Nullable
    private static Map<String, SavedPosition> savedPositions;

    private record SavedPosition(long position, long timestamp) {
    }

    /**
     * Injection point. Called when a new video starts playing.
     */
    public static void newVideoStarted(VideoInformation.PlaybackController ignoredPlayerController) {
        try {
            Utils.verifyOnMainThread();

            lastSavedTime = -1L;
            lastSaveAtMs = 0L;
            restoreAttempts = 0;
            restorePending = false;
            restoreSeekInProgress = false;
            newVideoGeneration++;

            if (!Settings.REMEMBER_PLAYBACK_POSITION.get()) {
                return;
            }

            restorePending = true;
            startRestoreCheck(newVideoGeneration);
        } catch (Exception ex) {
            Logger.printException(() -> "newVideoStarted failure", ex);
        }
    }

    /**
     * Injection point. Called approximately once per second during playback.
     * <p>
     * Caution: {@link VideoInformation#seekTo(long)} recursively re-enters this hook,
     * so a restore seek must not start another restore.
     */
    public static void videoTimeChanged(long playbackTimeMs) {
        try {
            if (!Settings.REMEMBER_PLAYBACK_POSITION.get()) {
                return;
            }
            if (VideoInformation.lastVideoIdIsShort()) {
                return;
            }
            if (VideoInformation.isLiveStream()) {
                return;
            }
            if (restorePending) {
                return;
            }

            final String videoId = VideoInformation.getVideoId();
            if (videoId.isEmpty()) {
                return;
            }

            final long videoLength = VideoInformation.getVideoLength();
            if (videoLength <= 0 || playbackTimeMs < MIN_PROGRESS_MS) {
                return;
            }

            if (playbackTimeMs >= videoLength - END_PADDING_MS) {
                // Video is finished; remove any stored position so a later rewatch starts fresh.
                deletePlaybackPosition(videoId);
                lastSavedTime = -1L;
                return;
            }

            final long now = System.currentTimeMillis();
            if (now - lastSaveAtMs < SAVE_INTERVAL_MS) {
                return;
            }
            if (Math.abs(playbackTimeMs - lastSavedTime) < SAVE_DELTA_MS) {
                return;
            }

            savePlaybackPosition(videoId, playbackTimeMs);
            lastSavedTime = playbackTimeMs;
            lastSaveAtMs = now;
        } catch (Exception ex) {
            Logger.printException(() -> "videoTimeChanged failure", ex);
        }
    }

    private static void startRestoreCheck(long generation) {
        Utils.runOnMainThreadDelayed(() -> checkRestore(generation), RESTORE_POLL_DELAY_MS);
    }

    private static void checkRestore(long generation) {
        try {
            if (generation != newVideoGeneration) {
                return;
            }
            if (!Settings.REMEMBER_PLAYBACK_POSITION.get() || VideoInformation.lastVideoIdIsShort()) {
                restorePending = false;
                return;
            }
            if (VideoInformation.isLiveStream()) {
                restorePending = false;
                return;
            }

            final String videoId = VideoInformation.getVideoId();
            final long videoLength = VideoInformation.getVideoLength();
            if (videoId.isEmpty() || videoLength <= 0) {
                rescheduleOrGiveUp(generation);
                return;
            }

            final SavedPosition saved = loadPlaybackPosition(videoId);
            if (saved == null || !isRestorable(saved.position(), videoLength)) {
                finishWithoutRestore(videoId, saved != null);
                return;
            }

            final long currentTime = VideoInformation.getVideoTime();
            final long delta = Math.abs(currentTime - saved.position());
            if (delta < ALREADY_RESTORED_TOLERANCE_MS) {
                restorePending = false;
                return;
            }
            if (currentTime >= RESTORE_WINDOW_MS) {
                Logger.printDebug(() -> "Restore window passed for " + videoId
                        + " current: " + currentTime + " saved: " + saved.position());
                finishWithoutRestore(videoId, false);
                return;
            }
            if (restoreSeekInProgress) {
                return;
            }

            attemptRestoreSeek(generation, videoId, saved.position(), 1);
        } catch (Exception ex) {
            restorePending = false;
            restoreSeekInProgress = false;
            Logger.printException(() -> "checkRestore failure", ex);
        }
    }

    private static boolean isRestorable(long savedPosition, long videoLength) {
        return savedPosition > MIN_PROGRESS_MS && savedPosition < videoLength - END_PADDING_MS;
    }

    private static void finishWithoutRestore(String videoId, boolean clearSaved) {
        restorePending = false;
        if (clearSaved) {
            deletePlaybackPosition(videoId);
        }
    }

    private static void attemptRestoreSeek(long generation, String videoId,
                                           long targetPositionMs, int attempt) {
        if (generation != newVideoGeneration) {
            return;
        }
        if (attempt == 1) {
            restoreSeekInProgress = true;
        }

        final long currentTime = VideoInformation.getVideoTime();
        if (currentTime >= RESTORE_WINDOW_MS) {
            Logger.printDebug(() -> "Restore window passed during seek for " + videoId);
            restoreSeekInProgress = false;
            finishWithoutRestore(videoId, false);
            return;
        }

        final boolean seekAccepted = VideoInformation.seekTo(targetPositionMs);
        Logger.printDebug(() -> "Restore attempt: " + attempt + "/" + RESTORE_SEEK_MAX_ATTEMPTS
                + " target: " + targetPositionMs + " current: " + currentTime
                + " accepted: " + seekAccepted);

        Utils.runOnMainThreadDelayed(() -> {
            if (generation != newVideoGeneration) {
                return;
            }

            final long timeAfterSeek = VideoInformation.getVideoTime();
            if (Math.abs(timeAfterSeek - targetPositionMs) <= ALREADY_RESTORED_TOLERANCE_MS) {
                Logger.printDebug(() -> "Restored playback position for " + videoId
                        + " to " + timeAfterSeek + "ms");
                restorePending = false;
                restoreSeekInProgress = false;
                return;
            }

            if (attempt < RESTORE_SEEK_MAX_ATTEMPTS) {
                attemptRestoreSeek(generation, videoId, targetPositionMs, attempt + 1);
                return;
            }

            Logger.printDebug(() -> "Restore seek gave up for " + videoId
                    + ". current: " + timeAfterSeek + " target: " + targetPositionMs);
            // Drop the saved position so the next open does not repeat a bad seek.
            restoreSeekInProgress = false;
            finishWithoutRestore(videoId, true);
        }, RESTORE_SEEK_RETRY_DELAY_MS);
    }

    private static void rescheduleOrGiveUp(long generation) {
        Utils.verifyOnMainThread();
        if (++restoreAttempts <= RESTORE_MAX_ATTEMPTS) {
            startRestoreCheck(generation);
            return;
        }

        Logger.printDebug(() -> "Restore polling gave up. videoId: "
                + VideoInformation.getVideoId() + " videoLength: " + VideoInformation.getVideoLength());
        restorePending = false;
        restoreSeekInProgress = false;
    }

    private static Map<String, SavedPosition> getSavedPositions() {
        if (savedPositions == null) {
            savedPositions = loadSavedPositions();
        }
        return savedPositions;
    }

    private static Map<String, SavedPosition> loadSavedPositions() {
        String raw = Settings.REMEMBER_PLAYBACK_POSITION_TIMES.get();
        if (raw.isEmpty()) {
            return new HashMap<>();
        }

        try {
            JSONObject json = new JSONObject(raw);
            Iterator<String> keys = json.keys();
            Map<String, SavedPosition> map = new HashMap<>(2 * json.length());
            while (keys.hasNext()) {
                String videoId = keys.next();
                JSONObject entry = json.optJSONObject(videoId);
                if (entry != null) {
                    final long position = entry.optLong("position", -1);
                    final long timestamp = entry.optLong("timestamp", -1);
                    if (position >= 0) {
                        map.put(videoId, new SavedPosition(position, timestamp));
                    }
                }
            }
            return map;
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to load playback positions setting", ex);
            Settings.REMEMBER_PLAYBACK_POSITION_TIMES.resetToDefault();
            return new HashMap<>();
        }
    }

    private static void saveSavedPositions() {
        if (savedPositions == null) {
            return;
        }

        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, SavedPosition> entry : savedPositions.entrySet()) {
                SavedPosition pos = entry.getValue();
                JSONObject entryJson = new JSONObject();
                entryJson.put("position", pos.position());
                entryJson.put("timestamp", pos.timestamp());
                json.put(entry.getKey(), entryJson);
            }
            Settings.REMEMBER_PLAYBACK_POSITION_TIMES.save(json.toString());
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to save playback positions setting", ex);
        }
    }

    private static void savePlaybackPosition(String videoId, long positionMs) {
        if (videoId.isEmpty()) {
            return;
        }

        Map<String, SavedPosition> map = getSavedPositions();
        map.put(videoId, new SavedPosition(positionMs, System.currentTimeMillis()));
        Logger.printDebug(() -> "Saved playback position: " + positionMs + " id: " + videoId);

        trimSavedPositions();
        saveSavedPositions();
    }

    @Nullable
    private static SavedPosition loadPlaybackPosition(String videoId) {
        return getSavedPositions().get(videoId);
    }

    private static void deletePlaybackPosition(String videoId) {
        Map<String, SavedPosition> map = getSavedPositions();
        if (map.remove(videoId) != null) {
            saveSavedPositions();
        }
    }

    private static void trimSavedPositions() {
        Map<String, SavedPosition> map = getSavedPositions();

        while (map.size() > MAX_SAVED_VIDEOS) {
            String oldestKey = null;
            long oldestTimestamp = Long.MAX_VALUE;
            for (Map.Entry<String, SavedPosition> entry : map.entrySet()) {
                if (entry.getValue().timestamp() < oldestTimestamp) {
                    oldestTimestamp = entry.getValue().timestamp();
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                map.remove(oldestKey);
            } else {
                break;
            }
        }
    }
}
