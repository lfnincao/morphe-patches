/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

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

    @Nullable
    private static volatile String pendingVideoId;
    private static volatile long pendingPositionMs = -1L;
    private static volatile boolean restorePending;
    private static volatile boolean seekAttempted;
    private static int restoreAttempts;

    private ServerResumePositionPatch() {
    }

    /**
     * Injection point. Called when the innertube player response bytes are built (off the main thread).
     */
    public static void onPlayerResponseReceived(byte[] playerResponse, String videoId,
                                                boolean isShortAndOpeningOrPlaying) {
        try {
            if (!Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get()) {
                return;
            }
            if (videoId == null || videoId.isEmpty()) {
                return;
            }
            if (VideoInformation.lastVideoIdIsShort()) {
                return;
            }

            final ServerResumePositionParser.ParseResult parsed =
                    ServerResumePositionParser.parse(playerResponse);
            ServerResumePositionProtoDump.logPlayerConfigOnce(
                    videoId, playerResponse, parsed.candidateFieldPath);

            final Long resumeMs = parsed.resumePositionMs;
            if (resumeMs == null) {
                return;
            }

            pendingVideoId = videoId;
            pendingPositionMs = resumeMs;
            Logger.printDebug(() -> "Server resume: found " + resumeMs + "ms for " + videoId);
        } catch (Exception ex) {
            Logger.printException(() -> "onPlayerResponseReceived failure", ex);
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

            if (!videoId.equals(pendingVideoId) || pendingPositionMs < ServerResumePositionParser.MIN_RESUME_MS) {
                return;
            }

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
                finishWithoutRestore();
                return;
            }

            final long currentTime = VideoInformation.getVideoTime();
            if (Math.abs(currentTime - targetPositionMs) < ALREADY_RESTORED_TOLERANCE_MS) {
                finishWithoutRestore();
                return;
            }

            if (currentTime >= RESTORE_WINDOW_MS) {
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