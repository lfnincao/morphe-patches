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
 * Experimental: seeks to the account's server watch progress of a video when no local
 * {@link RememberPlaybackPositionPatch} position was saved before the video started.
 * <p>
 * Sources, in order: the resume point of the player response, the position entity of the video on
 * its playlist page ({@link ServerPlaylistProgress}), the start time of the video on the playlist page,
 * and the watched fraction of the playlist page or of a thumbnail ({@link ServerWatchProgress}).
 */
@SuppressWarnings("unused")
public final class ServerResumePositionPatch {

    private static final long END_PADDING_MS = 10_000L;
    private static final long RESTORE_POLL_DELAY_MS = 500L;
    private static final long ALREADY_RESTORED_TOLERANCE_MS = 2_000L;
    /** A thumbnail this full is a finished video. */
    private static final float MAX_WATCHED_FRACTION = 0.95f;
    /**
     * How long after the start of a video the server position is still applied. Measured in wall
     * time, not playback time, because a SponsorBlock skip at the start moves the playback time
     * far ahead before the playlist page arrives.
     */
    private static final long RESTORE_WALL_WINDOW_MS = 20_000L;
    /** How long to wait for a source (player response, playlist page, thumbnails) after the start. */
    private static final long SOURCE_WAIT_MS = 12_000L;
    /** Give up if the video id or length is still unknown after this long. */
    private static final long SETUP_WAIT_MS = 15_000L;

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
    /** Wall time of newVideoStarted. */
    private static long videoStartedAt;
    /** Video id the local position check was done for, and its outcome. */
    @Nullable
    private static String localCheckVideoId;
    /** Video id the playlist page was requested for. */
    @Nullable
    private static String playlistRequestVideoId;

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

    /**
     * Injection point. Called with the start position that YouTube's PlaybackStartPositionResolver
     * returns (from the start time of the navigation, the client's saved position, or the player response).
     */
    public static void onYouTubeStartPositionResolved(@Nullable Long startPositionMs) {
        try {
            if (Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get()) {
                Logger.printDebug(() -> "Server resume: YouTube resolved start position "
                        + (startPositionMs == null ? "none" : startPositionMs + "ms")
                        + " for " + VideoInformation.getStartingOrPlayingVideoId());
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onYouTubeStartPositionResolved failure", ex);
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
            seekAttempted = false;
            videoStartedAt = System.currentTimeMillis();
            localCheckVideoId = null;
            playlistRequestVideoId = null;

            if (!Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get()) {
                clearPending();
                return;
            }

            // The video id, the player response, the playlist page and the list thumbnails of this
            // video arrive after this call, so everything is checked while polling in checkRestore.
            clearPending();
            restorePending = true;
            startRestoreCheck(newVideoGeneration);
        } catch (Exception ex) {
            Logger.printException(() -> "newVideoStarted failure", ex);
        }
    }

    private static void startRestoreCheck(long generation) {
        Utils.runOnMainThreadDelayed(() -> checkRestore(generation), RESTORE_POLL_DELAY_MS);
    }

    private static long sinceStart() {
        return System.currentTimeMillis() - videoStartedAt;
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
            final long videoLength = VideoInformation.getVideoLength();
            if (videoId.isEmpty() || videoLength <= 0) {
                if (sinceStart() > SETUP_WAIT_MS) {
                    Logger.printDebug(() -> "Server resume: gave up, video id '" + videoId
                            + "' or length " + videoLength + " unknown");
                    finishWithoutRestore();
                    return;
                }
                startRestoreCheck(generation);
                return;
            }

            // Start the playlist page fetch as early as possible.
            if (!videoId.equals(playlistRequestVideoId)) {
                playlistRequestVideoId = videoId;
                ServerPlaylistProgress.request(videoId, VideoInformation.getPlaylistId());
            }

            // Local position of RememberPlaybackPosition, as it was when the video started.
            if (!videoId.equals(localCheckVideoId)) {
                final Boolean hadLocal = RememberPlaybackPositionPatch.hadSavedPositionAtStart(videoId);
                if (hadLocal == null) {
                    if (sinceStart() > SETUP_WAIT_MS) {
                        Logger.printDebug(() -> "Server resume: gave up, local position check never ran for " + videoId);
                        finishWithoutRestore();
                        return;
                    }
                    startRestoreCheck(generation);
                    return;
                }
                if (hadLocal) {
                    Logger.printDebug(() -> "Server resume: skipped, a local position was saved before " + videoId
                            + " started (RememberPlaybackPosition restores it)");
                    finishWithoutRestore();
                    return;
                }
                localCheckVideoId = videoId;
                Logger.printDebug(() -> "Server resume: no local position at the start of " + videoId
                        + ", looking for a server position");
            }

            if (!videoId.equals(pendingVideoId)) {
                // 1. Resume point of the player response (playerConfig.playbackStartConfig.startPosition).
                // 2. Position entity of the video on the playlist page (exact).
                // 3. Start time of the video on the playlist page.
                // 4. Watched fraction of the playlist page, or of a thumbnail in a list (feed, panel).
                final Long serverPosition = getServerPosition(videoId);
                final ServerPlaylistProgress.Result playlist = ServerPlaylistProgress.get(videoId);
                final Float thumbnailFraction = ServerWatchProgress.getWatchedFraction(videoId);
                final boolean playlistDone = playlist == null || playlist.done;

                long target = -1;
                String source = null;
                if (serverPosition != null) {
                    target = serverPosition;
                    source = "player response";
                } else if (playlist != null && playlist.positionMs != null) {
                    target = playlist.positionMs;
                    source = "playlist page position entity";
                } else if (playlist != null && playlist.startTimeMs != null) {
                    target = playlist.startTimeMs;
                    source = "playlist page start time";
                } else if (playlistDone) {
                    Float fraction = null;
                    String fractionSource = null;
                    if (playlist != null && playlist.percent > 0 && playlist.percent <= 100) {
                        fraction = playlist.percent / 100f;
                        fractionSource = "playlist page percent " + playlist.percent;
                    } else if (thumbnailFraction != null) {
                        fraction = thumbnailFraction;
                        fractionSource = "thumbnail progress " + thumbnailFraction;
                    }
                    if (fraction != null && fraction < MAX_WATCHED_FRACTION) {
                        target = (long) (fraction * videoLength);
                        source = fractionSource;
                    }
                }

                if (source == null) {
                    if (!playlistDone || sinceStart() < SOURCE_WAIT_MS) {
                        // The player response, the playlist page or the thumbnails can still arrive.
                        startRestoreCheck(generation);
                        return;
                    }
                    Logger.printDebug(() -> "Server resume: no server position for " + videoId
                            + " (player response: none, playlist page: "
                            + (playlist == null ? "not requested" : playlist.status)
                            + ", thumbnail: " + (thumbnailFraction == null ? "none" : thumbnailFraction
                            + (thumbnailFraction >= MAX_WATCHED_FRACTION ? " counts as finished" : "")) + ")");
                    finishWithoutRestore();
                    return;
                }
                final long finalTarget = target;
                final String finalSource = source;
                if (target < ServerResumePositionParser.MIN_RESUME_MS) {
                    Logger.printDebug(() -> "Server resume: " + finalTarget + "ms from " + finalSource
                            + " is too early for " + videoId);
                    finishWithoutRestore();
                    return;
                }
                pendingVideoId = videoId;
                pendingPositionMs = target;
                Logger.printDebug(() -> "Server resume: target " + finalTarget + "ms for " + videoId
                        + " from " + finalSource + " (length " + videoLength + "ms)");
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

            if (currentTime > targetPositionMs) {
                // Playback is already past the server position (a SponsorBlock skip or a seek).
                Logger.printDebug(() -> "Server resume: playback is already at " + currentTime
                        + "ms, past the target " + targetPositionMs + "ms for " + videoId);
                finishWithoutRestore();
                return;
            }

            final long elapsed = sinceStart();
            if (elapsed > RESTORE_WALL_WINDOW_MS) {
                Logger.printDebug(() -> "Server resume: restore window passed for " + videoId
                        + " (" + elapsed + "ms after the start) current: " + currentTime
                        + " target: " + targetPositionMs);
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

    private static final int SEEK_MAX_ATTEMPTS = 6;
    private static final long SEEK_RETRY_DELAY_MS = 1_000L;

    private static void attemptSeek(long generation, String videoId, long targetPositionMs) {
        attemptSeek(generation, videoId, targetPositionMs, 1);
    }

    private static void attemptSeek(long generation, String videoId, long targetPositionMs, int attempt) {
        if (generation != newVideoGeneration) {
            return;
        }
        if (!videoId.equals(VideoInformation.getStartingOrPlayingVideoId())) {
            Logger.printDebug(() -> "Server resume: aborted after id change for " + videoId);
            finishWithoutRestore();
            return;
        }

        final long before = VideoInformation.getVideoTime();
        final boolean accepted = VideoInformation.seekTo(targetPositionMs);
        Logger.printDebug(() -> "Server resume: seeking to " + targetPositionMs + "ms for " + videoId
                + " (attempt " + attempt + ", from " + before + "ms, accepted: " + accepted + ")");

        // Early seeks are sometimes dropped while the player is still loading.
        Utils.runOnMainThreadDelayed(() -> {
            if (generation != newVideoGeneration || !videoId.equals(VideoInformation.getStartingOrPlayingVideoId())) {
                finishWithoutRestore();
                return;
            }
            final long now = VideoInformation.getVideoTime();
            if (Math.abs(now - targetPositionMs) <= ALREADY_RESTORED_TOLERANCE_MS + SEEK_RETRY_DELAY_MS * 2) {
                Logger.printDebug(() -> "Server resume: resumed " + videoId + " at " + now + "ms");
                synchronized (serverPositions) {
                    serverPositions.remove(videoId);
                }
                ServerWatchProgress.forget(videoId);
                finishWithoutRestore();
                return;
            }
            if (attempt < SEEK_MAX_ATTEMPTS && now < targetPositionMs && sinceStart() <= RESTORE_WALL_WINDOW_MS) {
                attemptSeek(generation, videoId, targetPositionMs, attempt + 1);
                return;
            }
            Logger.printDebug(() -> "Server resume: seek gave up for " + videoId + ", current: " + now
                    + " target: " + targetPositionMs);
            finishWithoutRestore();
        }, SEEK_RETRY_DELAY_MS);
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
