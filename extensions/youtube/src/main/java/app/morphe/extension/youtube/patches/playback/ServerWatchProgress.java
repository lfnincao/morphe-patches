/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.ComponentType;
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.Element;
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.ElementModel;
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.LockupViewModel;
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.ThumbnailOverlay;
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.VideoWithContextData;
import app.morphe.extension.youtube.patches.playback.ServerResumePositionParser.Field;
import app.morphe.extension.youtube.patches.utils.FeedVideoCardsPatch;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Server watch progress of videos, read from the red progress bar of their thumbnails in lists
 * (feeds, related videos, search) and of the rows of the playlist panel on the watch page.
 * <p>
 * The /player response of a video usually has no resume point, and the navigation of a playlist
 * (Next / Previous / autoplay) starts a video without a start time. The thumbnails of the list items
 * do carry the account's watch history, as a watched fraction:
 * <ul>
 *     <li>video_lockup_with_attachment.eml (classic rows, such as the library and related videos):
 *         {@code videoWithContextModel.data.videoData.thumbnail.progress}, video id from {@code data.onTap}.</li>
 *     <li>lockup.eml: {@code lockupViewModel.contentImage.thumbnailViewModel.overlays[]
 *         .thumbnailBottomOverlayViewModel.progressBar.thumbnailOverlayProgressBarViewModel.progress},
 *         video id from {@code lockupViewModel.contentId}.</li>
 *     <li>Playlist panel of the watch page (native views, not Litho): PlaylistPanelVideoRenderer
 *         {@code thumbnailOverlays[].thumbnailOverlayResumePlaybackRenderer.percentDurationWatched},
 *         see {@link #onPlaylistPanelVideo(String, int)}.</li>
 * </ul>
 */
public final class ServerWatchProgress {

    /** NavigationEndpoint extension field of WatchEndpoint (YouTube 21.30: bncz). */
    private static final int WATCH_ENDPOINT_FIELD = 48687757;
    private static final int WATCH_ENDPOINT_VIDEO_ID_FIELD = 1;
    private static final int MAX_ON_TAP_DEPTH = 6;

    private static final int MAX_VIDEOS = 300;
    private static final int MAX_LOGGED_TEMPLATES = 40;

    private static final Map<String, Float> progressByVideoId =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
                    return size() > MAX_VIDEOS;
                }
            };

    /** Templates seen with a video, for the debug log. */
    private static final Set<String> loggedTemplates = new LinkedHashSet<>();

    private static final int MAX_LOGGED_COMPACT_IDS = 200;
    private static final Set<String> loggedCompactIds = new LinkedHashSet<>();

    private ServerWatchProgress() {
    }

    /**
     * Injection point. Called for every element the app parses. Never changes the element.
     */
    public static byte[] onElement(byte[] bytes) {
        try {
            if (bytes == null || !Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get()) {
                return bytes;
            }
            // Both templates include "lockup". Checking the text first avoids parsing most elements.
            final String text = FeedVideoCardsPatch.getElementText(bytes);
            if (!text.contains("lockup")) {
                return bytes;
            }
            readElement(bytes);
        } catch (Exception ex) {
            Logger.printException(() -> "onElement failure", ex);
        }
        return bytes;
    }

    private static void readElement(byte[] bytes) throws Exception {
        final Element element = Element.parseFrom(bytes);
        final ComponentType componentType = element.getType().getComponentType();
        final ElementModel model = componentType.getModel();
        final String uri = componentType.getTemplateConfig().getUriTemplateConfig().getUri();

        String videoId = null;
        float progress = 0f;
        boolean compact = false;
        if (model.hasVideoWithContextModel()) {
            final VideoWithContextData data = model.getVideoWithContextModel().getData();
            compact = data.getCompact();
            progress = data.getVideoData().getThumbnail().getProgress();
            videoId = findWatchEndpointVideoId(data.getOnTap().toByteArray());
        } else if (model.hasLockupModel()) {
            final LockupViewModel lockup = model.getLockupModel().getViewModel().getLockupViewModel();
            videoId = lockup.getContentId();
            for (ThumbnailOverlay overlay : lockup.getContentImage().getThumbnailViewModel().getOverlaysList()) {
                if (overlay.hasThumbnailBottomOverlayViewModel()) {
                    final float p = overlay.getThumbnailBottomOverlayViewModel().getProgressBar()
                            .getThumbnailOverlayProgressBarViewModel().getProgress();
                    if (p > 0) {
                        progress = p;
                    }
                }
            }
        } else {
            return;
        }

        if (videoId == null || videoId.length() != 11) {
            return;
        }
        logTemplateOnce(uri, videoId, progress);
        if (!(progress > 0f) || progress > 1f) {
            // Rows of the playlist panel and the library are compact. Log them once even without
            // progress, so a debug log shows if the rows of a playlist were seen at all.
            if (compact && Logger.isDebugEnabled()) {
                final String id = videoId;
                synchronized (loggedCompactIds) {
                    if (loggedCompactIds.size() < MAX_LOGGED_COMPACT_IDS && loggedCompactIds.add(id)) {
                        Logger.printDebug(() -> "Server watch progress: " + id + " not watched (compact row "
                                + shortUri(uri) + ")");
                    }
                }
            }
            return;
        }

        final String id = videoId;
        final float fraction = progress;
        final Float previous;
        synchronized (progressByVideoId) {
            previous = progressByVideoId.put(id, fraction);
        }
        if (previous == null || previous != fraction) {
            final boolean isCompact = compact;
            Logger.printDebug(() -> "Server watch progress: " + id + " watched " + fraction
                    + " (" + (isCompact ? "compact row " : "") + shortUri(uri) + ")");
        }
    }

    private static final int MAX_LOGGED_PANEL_IDS = 200;
    private static final Set<String> loggedPanelIds = new LinkedHashSet<>();

    /**
     * Injection point. Called when a row of the playlist panel on the watch page is bound.
     * The playlist panel does not use Litho: its rows are PlaylistPanelVideoRenderer protos of the
     * /next response, with the watch progress in thumbnailOverlays[].thumbnailOverlayResumePlaybackRenderer
     * .percentDurationWatched.
     *
     * @param videoId                PlaylistPanelVideoRenderer.videoId.
     * @param percentDurationWatched 0..100, or -1 if the row has no resume overlay.
     */
    public static void onPlaylistPanelVideo(@Nullable String videoId, int percentDurationWatched) {
        try {
            if (videoId == null || videoId.length() != 11
                    || !Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get()) {
                return;
            }
            if (percentDurationWatched <= 0 || percentDurationWatched > 100) {
                if (Logger.isDebugEnabled()) {
                    synchronized (loggedPanelIds) {
                        if (loggedPanelIds.size() >= MAX_LOGGED_PANEL_IDS || !loggedPanelIds.add(videoId)) {
                            return;
                        }
                    }
                    Logger.printDebug(() -> "Server watch progress: " + videoId + " not watched (playlist panel, "
                            + (percentDurationWatched < 0 ? "no resume overlay" : "percent " + percentDurationWatched)
                            + ")");
                }
                return;
            }
            final float fraction = percentDurationWatched / 100f;
            final Float previous;
            synchronized (progressByVideoId) {
                previous = progressByVideoId.put(videoId, fraction);
            }
            if (previous == null || previous != fraction) {
                Logger.printDebug(() -> "Server watch progress: " + videoId + " watched " + fraction
                        + " (playlist panel, " + percentDurationWatched + "%)");
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onPlaylistPanelVideo failure", ex);
        }
    }

    /**
     * @return The watched fraction (0..1] from the thumbnail of the video, or null if not seen.
     */
    @Nullable
    public static Float getWatchedFraction(String videoId) {
        synchronized (progressByVideoId) {
            return progressByVideoId.get(videoId);
        }
    }

    static void forget(String videoId) {
        synchronized (progressByVideoId) {
            progressByVideoId.remove(videoId);
        }
    }

    @Nullable
    private static String findWatchEndpointVideoId(byte[] command) {
        return findWatchEndpointVideoId(Field.parse(command), 0);
    }

    @Nullable
    private static String findWatchEndpointVideoId(@Nullable List<Field> fields, int depth) {
        if (fields == null || depth > MAX_ON_TAP_DEPTH) {
            return null;
        }
        for (Field field : fields) {
            if (field.wireType != Field.WIRE_LENGTH_DELIMITED) {
                continue;
            }
            if (field.number == WATCH_ENDPOINT_FIELD) {
                final List<Field> watchEndpoint = Field.parse(field.bytes);
                if (watchEndpoint != null) {
                    final Field id = Field.find(watchEndpoint, WATCH_ENDPOINT_VIDEO_ID_FIELD,
                            Field.WIRE_LENGTH_DELIMITED);
                    if (id != null && id.bytes != null && id.bytes.length == 11) {
                        return new String(id.bytes, java.nio.charset.StandardCharsets.US_ASCII);
                    }
                }
                continue;
            }
            if (field.bytes != null && field.bytes.length > 12) {
                final String found = findWatchEndpointVideoId(Field.parse(field.bytes), depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void logTemplateOnce(String uri, String videoId, float progress) {
        if (!Logger.isDebugEnabled()) {
            return;
        }
        final String template = shortUri(uri);
        synchronized (loggedTemplates) {
            if (loggedTemplates.size() >= MAX_LOGGED_TEMPLATES || !loggedTemplates.add(template)) {
                return;
            }
        }
        Logger.printDebug(() -> "Server watch progress: template " + template + " has video ids (first: "
                + videoId + " progress " + progress + ")");
    }

    private static String shortUri(String uri) {
        if (uri == null) {
            return "";
        }
        final int query = uri.indexOf('?');
        return query >= 0 ? uri.substring(0, query) : uri;
    }
}
