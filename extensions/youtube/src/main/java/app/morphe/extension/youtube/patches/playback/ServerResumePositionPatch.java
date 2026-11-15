/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Experimental: seeks once to YouTube's server-provided watch-history start position when no local
 * {@link RememberPlaybackPositionPatch} restore will run.
 */
@SuppressWarnings("unused")
public final class ServerResumePositionPatch {

    private static final long END_PADDING_MS = 10_000L;
    private static final long RESTORE_WINDOW_MS = 5_000L;
    private static final long RESTORE_POLL_DELAY_MS = 500L;
    private static final int RESTORE_MAX_ATTEMPTS = 20;
    private static final long ALREADY_RESTORED_TOLERANCE_MS = 2_000L;

    private static long newVideoGeneration;

    /** Server positions by video id. Responses of queued or prefetched videos arrive early. */
    private static final int MAX_SERVER_POSITIONS = 30;
    private static final Map<String, Long> serverPositions =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_SERVER_POSITIONS;
                }
            };

    @Nullable
    private static volatile String pendingVideoId;
    private static volatile long pendingPositionMs = -1L;
    private static volatile boolean restorePending;
    private static volatile boolean seekAttempted;
    private static int restoreAttempts;

    private ServerResumePositionPatch() {
    }

    /**
     * Injection point.
     */
    public static boolean isEnabled() {
        return Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get();
    }

    /**
     * Injection point. Called when the app builds its player response model from a /player response,
     * before "Spoof video streams" can replace the player config.
     *
     * @param playerConfig Serialized {@code playerResponse.playerConfig} of the app, or null if none.
     * @param videoId      {@code playerResponse.videoDetails.videoId}, or null if none.
     */
    public static void onAppPlayerConfig(@Nullable byte[] playerConfig, @Nullable String videoId) {
        try {
            if (videoId == null || videoId.isEmpty()) {
                return;
            }

            final ServerResumePositionParser.ParseResult parsed =
                    ServerResumePositionParser.parsePlayerConfig(playerConfig);
            ServerResumePositionProtoDump.logOnce(videoId, playerConfig, parsed);

            final Long resumeMs = parsed.resumePositionMs;
            synchronized (serverPositions) {
                if (resumeMs == null) {
                    serverPositions.remove(videoId);
                } else {
                    serverPositions.put(videoId, resumeMs);
                }
            }

            if (resumeMs == null) {
                Logger.printDebug(() -> "Server resume: none for " + videoId
                        + (parsed.rawPositionMs != null ? " (ignored " + parsed.rawPositionMs + "ms)" : ""));
                return;
            }
            Logger.printDebug(() -> "Server resume: found " + resumeMs + "ms for " + videoId
                    + " at playerConfig." + parsed.fieldPath);
        } catch (Exception ex) {
            Logger.printException(() -> "onAppPlayerConfig failure", ex);
        }
    }

    @Nullable
    private static Long getServerPosition(String videoId) {
        synchronized (serverPositions) {
            return serverPositions.get(videoId);
        }
    }

    /**
     * Injection point. Called when a new video starts playing.
     */
    public static void newVideoStarted(VideoInformation.PlaybackController ignoredPlayerController) {
        try {
            Utils.verifyOnMainThread();

            newVideoGeneration++;
            restorePending = false;
            restoreAttempts = 0;
            seekAttempted = false;

            if (!Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get()) {
                clearPending();
                return;
            }

            final String videoId = VideoInformation.getStartingOrPlayingVideoId();
            if (videoId.isEmpty() || VideoInformation.lastVideoIdIsShort()) {
                clearPending();
                return;
            }
            if (VideoInformation.isLiveStream()) {
                clearPending();
                return;
            }

            if (RememberPlaybackPositionPatch.shouldDeferToLocalRestore(videoId)) {
                Logger.printDebug(() -> "Server resume: skipped (local position exists) for " + videoId);
                clearPending();
                return;
            }

            final Long serverPosition = getServerPosition(videoId);
            if (serverPosition == null || serverPosition < ServerResumePositionParser.MIN_RESUME_MS) {
                clearPending();
                return;
            }
            pendingVideoId = videoId;
            pendingPositionMs = serverPosition;

            restorePending = true;
            startRestoreCheck(newVideoGeneration);
        } catch (Exception ex) {
            Logger.printException(() -> "newVideoStarted failure", ex);
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
            if (!Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get()) {
                finishWithoutRestore();
                return;
            }
            if (VideoInformation.lastVideoIdIsShort() || VideoInformation.isLiveStream()) {
                finishWithoutRestore();
                return;
            }

            final String videoId = VideoInformation.getStartingOrPlayingVideoId();
            if (videoId.isEmpty()) {
                rescheduleOrGiveUp(generation);
                return;
            }

            if (RememberPlaybackPositionPatch.shouldDeferToLocalRestore(videoId)) {
                Logger.printDebug(() -> "Server resume: skipped (local position exists) for " + videoId);
                finishWithoutRestore();
                return;
            }

            if (!videoId.equals(pendingVideoId) || pendingPositionMs < ServerResumePositionParser.MIN_RESUME_MS) {
                finishWithoutRestore();
                return;
            }

            final long videoLength = VideoInformation.getVideoLength();
            if (videoLength <= 0) {
                rescheduleOrGiveUp(generation);
                return;
            }

            final long targetPositionMs = pendingPositionMs;
            if (targetPositionMs >= videoLength - END_PADDING_MS) {
                Logger.printDebug(() -> "Server resume: " + targetPositionMs + "ms is at the end of "
                        + videoId + " (length " + videoLength + "ms)");
                finishWithoutRestore();
                return;
            }

            final long currentTime = VideoInformation.getVideoTime();
            if (Math.abs(currentTime - targetPositionMs) < ALREADY_RESTORED_TOLERANCE_MS) {
                Logger.printDebug(() -> "Server resume: already at " + currentTime + "ms for " + videoId);
                finishWithoutRestore();
                return;
            }

            if (currentTime >= RESTORE_WINDOW_MS) {
                Logger.printDebug(() -> "Server resume: restore window passed for " + videoId
                        + " current: " + currentTime + " target: " + targetPositionMs);
                finishWithoutRestore();
                return;
            }

            if (seekAttempted) {
                return;
            }
            seekAttempted = true;
            attemptSeek(generation, videoId, targetPositionMs);
        } catch (Exception ex) {
            finishWithoutRestore();
            Logger.printException(() -> "checkRestore failure", ex);
        }
    }

    private static void attemptSeek(long generation, String videoId, long targetPositionMs) {
        if (generation != newVideoGeneration) {
            return;
        }
        if (!videoId.equals(VideoInformation.getStartingOrPlayingVideoId())) {
            Logger.printDebug(() -> "Server resume: aborted after id change for " + videoId);
            finishWithoutRestore();
            return;
        }

        Logger.printDebug(() -> "Server resume: seeking to " + targetPositionMs + "ms for " + videoId);
        VideoInformation.seekTo(targetPositionMs);
        synchronized (serverPositions) {
            serverPositions.remove(videoId);
        }
        clearPending();
        restorePending = false;
    }

    private static void rescheduleOrGiveUp(long generation) {
        Utils.verifyOnMainThread();
        if (++restoreAttempts <= RESTORE_MAX_ATTEMPTS) {
            startRestoreCheck(generation);
            return;
        }
        finishWithoutRestore();
    }

    private static void finishWithoutRestore() {
        restorePending = false;
        clearPending();
    }

    private static void clearPending() {
        pendingVideoId = null;
        pendingPositionMs = -1L;
    }
}