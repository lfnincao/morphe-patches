/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.innertube.utils.AuthUtils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.youtube.patches.playback.ServerResumePositionParser.Field;
import app.morphe.extension.youtube.patches.utils.requests.PlaylistRoutes;

/**
 * Server watch progress of the videos of a playlist, read from its playlist page (browse VL&lt;id&gt;)
 * with the account of the app.
 * <p>
 * The playlist panel of the watch page gets no watch progress from the server (its rows have no
 * resume overlay), and the app's PlaylistPanelVideoRenderer can't hold one anyway. The playlist page
 * does: each watched {@code playlistVideoRenderer} has a
 * {@code thumbnailOverlayResumePlaybackRenderer} whose {@code percentDurationWatched} is 0 and whose
 * {@code videoPlaybackPositionToggleEntityKey} points to an entity of the response
 * ({@code frameworkUpdates.entityBatchUpdate.mutations[].payload.videoPlaybackPositionEntity
 * .lastPlaybackPositionSeconds}). Partially watched videos can also have
 * {@code navigationEndpoint.watchEndpoint.startTimeSeconds}.
 */
public final class ServerPlaylistProgress {

    private static final String RENDERER_FIELDS = "contents.playlistVideoRenderer(videoId,lengthSeconds,"
            + "thumbnailOverlays.thumbnailOverlayResumePlaybackRenderer,"
            + "navigationEndpoint.watchEndpoint.startTimeSeconds),continuations";

    private static final Route.CompiledRoute BROWSE_PLAYLIST_PROGRESS = new Route(
            Route.Method.POST,
            "browse?fields=contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.sectionListRenderer"
                    + ".contents(playlistVideoListRenderer(" + RENDERER_FIELDS + "),"
                    + "itemSectionRenderer.contents.playlistVideoListRenderer(" + RENDERER_FIELDS + "))"
                    + ",continuationContents.playlistVideoListContinuation(" + RENDERER_FIELDS + ")"
                    + ",frameworkUpdates.entityBatchUpdate.mutations&prettyPrint=false"
    ).compile();

    /** A page after the first has up to 100 videos. */
    private static final int MAX_PAGES = 30;
    /** A finished request is reused for this long, such as for the retries of one video start. */
    private static final long RESULT_REUSE_MS = 15_000L;
    /**
     * If the video is this close to the end of its page, the next page is fetched too,
     * so the start time of the next video is cached before Next or autoplay starts it.
     */
    private static final int NEXT_PAGE_MARGIN_ROWS = 3;
    /** Cached start times of the rows of the playlist page are used for this long. */
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final int MAX_CACHED_ROWS = 5_000;

    /** Start position of a video of the playlist, as of the last fetch of its page. */
    public static final class CachedRow {
        public final String videoId;
        /** Start position, or -1 if the row has none (not watched, or finished). */
        public final long startMs;
        /** Where the start position comes from, for the debug log. */
        public final String source;
        /** Length of the video according to the playlist page, or 0. */
        public final long lengthMs;
        public final long fetchedAt;

        CachedRow(String videoId, long startMs, String source, long lengthMs, long fetchedAt) {
            this.videoId = videoId;
            this.startMs = startMs;
            this.source = source;
            this.lengthMs = lengthMs;
            this.fetchedAt = fetchedAt;
        }

        public long ageMs() {
            return System.currentTimeMillis() - fetchedAt;
        }
    }

    /** Rows of the playlist pages fetched last, of a single playlist. */
    private static final class PlaylistCache {
        final String playlistId;
        final Map<String, CachedRow> rows = new HashMap<>();

        PlaylistCache(String playlistId) {
            this.playlistId = playlistId;
        }
    }

    @Nullable
    private static PlaylistCache playlistCache;

    /** Result of the playlist page for one video. */
    public static final class Result {
        public final String videoId;
        public final boolean done;
        /** If the video was found in the playlist. */
        public final boolean found;
        /** lastPlaybackPosition of the position entity of the video, or null. */
        @Nullable
        public final Long positionMs;
        /** watchEndpoint.startTimeSeconds of the video, or null. */
        @Nullable
        public final Long startTimeMs;
        /** percentDurationWatched of the resume overlay, or -1 if it has none. */
        public final int percent;
        /** Length of the video according to the playlist page, or 0. */
        public final long lengthMs;
        /** Human readable status for the debug log. */
        public final String status;
        final long finishedAt;

        Result(String videoId, boolean done, boolean found, @Nullable Long positionMs, @Nullable Long startTimeMs,
               int percent, long lengthMs, String status) {
            this.videoId = videoId;
            this.done = done;
            this.found = found;
            this.positionMs = positionMs;
            this.startTimeMs = startTimeMs;
            this.percent = percent;
            this.lengthMs = lengthMs;
            this.status = status;
            this.finishedAt = done ? System.currentTimeMillis() : 0;
        }

        @Override
        public String toString() {
            return status;
        }
    }

    private static final Map<String, Result> results = Collections.synchronizedMap(new HashMap<>());

    private ServerPlaylistProgress() {
    }

    /**
     * Starts a fetch of the playlist page for the video, unless one is running or just finished.
     *
     * @return The current result (possibly not done yet), or null if there is no playlist to fetch.
     */
    @Nullable
    public static Result request(String videoId, @Nullable String playlistId) {
        if (videoId == null || videoId.isEmpty()) {
            return null;
        }
        final Result existing = results.get(videoId);
        if (existing != null && (!existing.done
                || System.currentTimeMillis() - existing.finishedAt < RESULT_REUSE_MS)) {
            return existing;
        }

        if (playlistId == null || playlistId.isEmpty()) {
            return put(new Result(videoId, true, false, null, null, -1, 0, "no playlist"));
        }
        if (playlistId.startsWith("RD")) {
            // Mixes have no playlist page.
            return put(new Result(videoId, true, false, null, null, -1, 0, "mix " + playlistId + " has no playlist page"));
        }
        if (AuthUtils.isNotLoggedIn()) {
            Logger.printDebug(() -> "Server playlist progress: not signed in, not fetching " + playlistId);
            return put(new Result(videoId, true, false, null, null, -1, 0, "not signed in"));
        }

        final Map<String, String> headers = AuthUtils.getRequestHeader();
        final Result pending = put(new Result(videoId, false, false, null, null, -1, 0,
                "fetching " + playlistId));
        Logger.printDebug(() -> "Server playlist progress: fetching playlist " + playlistId + " for " + videoId);
        Utils.runOnBackgroundThread(() -> {
            Result result;
            try {
                result = fetch(videoId, playlistId, headers);
            } catch (Exception ex) {
                Logger.printInfo(() -> "Server playlist progress: fetch failed for " + videoId, ex);
                result = new Result(videoId, true, false, null, null, -1, 0, "fetch failed: " + ex);
            }
            put(result);
            final Result finalResult = result;
            Logger.printDebug(() -> "Server playlist progress: " + videoId + " " + finalResult.status);
        });
        return pending;
    }

    @Nullable
    public static Result get(String videoId) {
        return results.get(videoId);
    }

    /**
     * Cached row of the video in the playlist, from an earlier fetch of the playlist page.
     * Never blocks or fetches, so it can be called while the app builds a player response.
     *
     * @param playlistId Playlist of the video, or empty if not known yet (then the cached playlist is used).
     * @return The row, or null if the playlist page with the video was not fetched recently.
     */
    @Nullable
    public static CachedRow getCached(@Nullable String videoId, @Nullable String playlistId) {
        if (videoId == null || videoId.isEmpty()) {
            return null;
        }
        synchronized (ServerPlaylistProgress.class) {
            final PlaylistCache cache = playlistCache;
            if (cache == null || (playlistId != null && !playlistId.isEmpty() && !cache.playlistId.equals(playlistId))) {
                return null;
            }
            final CachedRow row = cache.rows.get(videoId);
            if (row == null || row.ageMs() > CACHE_TTL_MS) {
                return null;
            }
            return row;
        }
    }

    /** TEST ONLY. */
    public static CachedRow fakeRowForTest(String videoId) {
        return new CachedRow(videoId, 60_000L, "FAKE test row", 0, System.currentTimeMillis());
    }

    /** Playlist id of the cached rows, for the debug log. */
    @Nullable
    public static String getCachedPlaylistId() {
        synchronized (ServerPlaylistProgress.class) {
            return playlistCache == null ? null : playlistCache.playlistId;
        }
    }

    private static void updateCache(String playlistId, Map<String, Row> rows, Map<String, Long> positionsByVideoId,
                                    Map<String, Long> positionsByKey) {
        final long now = System.currentTimeMillis();
        synchronized (ServerPlaylistProgress.class) {
            PlaylistCache cache = playlistCache;
            if (cache == null || !cache.playlistId.equals(playlistId) || cache.rows.size() > MAX_CACHED_ROWS) {
                cache = new PlaylistCache(playlistId);
                playlistCache = cache;
            }
            for (Map.Entry<String, Row> entry : rows.entrySet()) {
                final String id = entry.getKey();
                final Row row = entry.getValue();
                long startMs = -1;
                String source = "none";
                Long positionMs = positionsByVideoId.get(id);
                if (positionMs == null && row.positionKey != null) {
                    positionMs = positionsByKey.get(normalizeKey(row.positionKey));
                }
                if (positionMs != null) {
                    startMs = positionMs;
                    source = "position entity";
                } else if (row.startTimeMs != null) {
                    startMs = row.startTimeMs;
                    source = "start time";
                }
                cache.rows.put(id, new CachedRow(id, startMs, source, row.lengthMs, now));
            }
        }
    }

    private static Result put(Result result) {
        synchronized (results) {
            if (results.size() > 50) {
                results.clear();
            }
            results.put(result.videoId, result);
        }
        return result;
    }

    /** Row of the playlist page. */
    private static final class Row {
        long lengthMs;
        @Nullable
        Long startTimeMs;
        int percent = -1;
        @Nullable
        String positionKey;
    }

    private static Result fetch(String videoId, String playlistId, Map<String, String> headers) throws Exception {
        Utils.verifyOffMainThread();
        final long start = System.currentTimeMillis();

        // Position entities by video id and by entity key, of all fetched pages.
        final Map<String, Long> positionsByVideoId = new HashMap<>();
        final Map<String, Long> positionsByKey = new HashMap<>();
        final Map<String, Integer> mutationTypes = new TreeMap<>();
        String firstPositionPayload = null;

        int rows = 0;
        int overlays = 0;
        int overlayKeys = 0;
        int startTimes = 0;
        Row target = null;
        int targetPage = -1;
        int targetIndexInPage = -1;
        final Map<String, Row> rowsById = new HashMap<>();

        JSONObject response = send(PlaylistRoutes.browsePlaylistBody(playlistId), headers);
        if (response == null) {
            return new Result(videoId, true, false, null, null, -1, 0,
                    "playlist page request failed for " + playlistId);
        }
        JSONObject page = findPlaylistRenderer(response);
        for (int pageIndex = 0; pageIndex < MAX_PAGES; pageIndex++) {
            // Entities.
            final JSONObject frameworkUpdates = response.optJSONObject("frameworkUpdates");
            final JSONObject batch = frameworkUpdates == null ? null : frameworkUpdates.optJSONObject("entityBatchUpdate");
            final JSONArray mutations = batch == null ? null : batch.optJSONArray("mutations");
            if (mutations != null) {
                for (int i = 0, n = mutations.length(); i < n; i++) {
                    final JSONObject mutation = mutations.optJSONObject(i);
                    final JSONObject payload = mutation == null ? null : mutation.optJSONObject("payload");
                    if (payload == null) {
                        continue;
                    }
                    final Iterator<String> types = payload.keys();
                    while (types.hasNext()) {
                        final String type = types.next();
                        final Integer count = mutationTypes.get(type);
                        mutationTypes.put(type, count == null ? 1 : count + 1);
                        final JSONObject entity = payload.optJSONObject(type);
                        if (entity == null) {
                            continue;
                        }
                        final Long positionMs = findPositionMs(entity);
                        if (positionMs == null) {
                            continue;
                        }
                        if (firstPositionPayload == null) {
                            firstPositionPayload = type + " " + truncate(entity.toString(), 300);
                        }
                        final String entityKey = mutation.optString("entityKey", entity.optString("key"));
                        if (!entityKey.isEmpty()) {
                            positionsByKey.put(normalizeKey(entityKey), positionMs);
                        }
                        String id = entity.optString("videoId");
                        if (id.length() != 11) {
                            id = videoIdOfEntityKey(entityKey);
                        }
                        if (id != null && id.length() == 11) {
                            positionsByVideoId.put(id, positionMs);
                        }
                    }
                }
            }

            // Rows.
            final JSONArray contents = page == null ? null : page.optJSONArray("contents");
            int pageRows = 0;
            if (contents != null) {
                for (int i = 0, n = contents.length(); i < n; i++) {
                    final JSONObject element = contents.optJSONObject(i);
                    final JSONObject renderer = element == null ? null : element.optJSONObject("playlistVideoRenderer");
                    if (renderer == null) {
                        continue;
                    }
                    rows++;
                    pageRows++;
                    final Row row = parseRow(renderer);
                    final String rowVideoId = renderer.optString("videoId");
                    if (rowVideoId.length() == 11) {
                        rowsById.put(rowVideoId, row);
                    }
                    if (row.percent >= 0) {
                        overlays++;
                    }
                    if (row.positionKey != null) {
                        overlayKeys++;
                    }
                    if (row.startTimeMs != null) {
                        startTimes++;
                    }
                    if (target == null && videoId.equals(rowVideoId)) {
                        target = row;
                        targetPage = pageIndex;
                        targetIndexInPage = pageRows - 1;
                    }
                }
            }
            if (target != null) {
                if (targetPage != pageIndex) {
                    break; // The page after the page of the video, for the next video.
                }
                if (targetIndexInPage < pageRows - NEXT_PAGE_MARGIN_ROWS) {
                    break;
                }
            }

            final JSONArray continuations = page == null ? null : page.optJSONArray("continuations");
            final JSONObject continuation = continuations == null || continuations.length() == 0
                    ? null : continuations.optJSONObject(0);
            final JSONObject next = continuation == null ? null : continuation.optJSONObject("nextContinuationData");
            final String token = next == null ? "" : next.optString("continuation");
            if (token.isEmpty()) {
                break;
            }
            response = send(PlaylistRoutes.browseContinuationBody(token), headers);
            if (response == null) {
                break;
            }
            final JSONObject continuationContents = response.optJSONObject("continuationContents");
            page = continuationContents == null
                    ? null : continuationContents.optJSONObject("playlistVideoListContinuation");
        }

        updateCache(playlistId, rowsById, positionsByVideoId, positionsByKey);

        final long elapsed = System.currentTimeMillis() - start;
        final String summary = rows + " rows (" + rowsById.size() + " cached), " + overlays + " resume overlays (" + overlayKeys + " with a position key), "
                + startTimes + " start times, " + positionsByVideoId.size() + " position entities, mutation types "
                + mutationTypes + ", " + elapsed + "ms"
                + (firstPositionPayload != null ? ", first position entity: " + firstPositionPayload : "");

        if (target == null) {
            return new Result(videoId, true, false, null, null, -1, 0,
                    "not found in playlist " + playlistId + " (" + summary + ")");
        }

        Long positionMs = positionsByVideoId.get(videoId);
        if (positionMs == null && target.positionKey != null) {
            positionMs = positionsByKey.get(normalizeKey(target.positionKey));
        }
        final String status = "found on page " + targetPage + " of " + playlistId
                + ": position " + (positionMs == null ? "none" : positionMs + "ms")
                + ", startTimeSeconds " + (target.startTimeMs == null ? "none" : target.startTimeMs / 1000)
                + ", percent " + (target.percent < 0 ? "none (no resume overlay)" : target.percent)
                + ", position key " + (target.positionKey == null ? "none" : target.positionKey)
                + ", length " + target.lengthMs + "ms (" + summary + ")";
        return new Result(videoId, true, true, positionMs, target.startTimeMs, target.percent, target.lengthMs, status);
    }

    private static Row parseRow(JSONObject renderer) {
        final Row row = new Row();
        row.lengthMs = parseLong(renderer.opt("lengthSeconds"), 0) * 1000;
        final JSONArray thumbnailOverlays = renderer.optJSONArray("thumbnailOverlays");
        if (thumbnailOverlays != null) {
            for (int j = 0, m = thumbnailOverlays.length(); j < m; j++) {
                final JSONObject overlay = thumbnailOverlays.optJSONObject(j);
                final JSONObject resume = overlay == null
                        ? null : overlay.optJSONObject("thumbnailOverlayResumePlaybackRenderer");
                if (resume != null) {
                    row.percent = (int) parseLong(resume.opt("percentDurationWatched"), 0);
                    final String key = resume.optString("videoPlaybackPositionToggleEntityKey");
                    if (!key.isEmpty()) {
                        row.positionKey = key;
                    }
                }
            }
        }
        final JSONObject navigationEndpoint = renderer.optJSONObject("navigationEndpoint");
        final JSONObject watchEndpoint = navigationEndpoint == null ? null : navigationEndpoint.optJSONObject("watchEndpoint");
        if (watchEndpoint != null && watchEndpoint.has("startTimeSeconds")) {
            final long seconds = parseLong(watchEndpoint.opt("startTimeSeconds"), -1);
            if (seconds > 0) {
                row.startTimeMs = seconds * 1000;
            }
        }
        return row;
    }

    /**
     * @return A playback position of the entity, from a field such as lastPlaybackPositionSeconds.
     */
    @Nullable
    static Long findPositionMs(JSONObject entity) {
        final Iterator<String> keys = entity.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final String lower = key.toLowerCase(Locale.US);
            if (!lower.contains("position") || lower.contains("key")) {
                continue;
            }
            final Object value = entity.opt(key);
            if (value instanceof JSONObject || value instanceof JSONArray) {
                continue;
            }
            final long number = parseLong(value, -1);
            if (number < 0) {
                continue;
            }
            if (lower.endsWith("ms") || lower.endsWith("millis") || lower.endsWith("milliseconds")) {
                return number;
            }
            if (lower.endsWith("seconds") || lower.endsWith("secs") || lower.endsWith("sec")) {
                return number * 1000;
            }
        }
        return null;
    }

    private static long parseLong(@Nullable Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isEmpty()) {
            try {
                return (long) Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static String normalizeKey(String key) {
        try {
            return URLDecoder.decode(key, "UTF-8");
        } catch (Exception ex) {
            return key;
        }
    }

    /**
     * @return The video id of an entity key (a serialized message whose field 2 is the entity id).
     */
    @Nullable
    static String videoIdOfEntityKey(@Nullable String entityKey) {
        if (entityKey == null || entityKey.isEmpty()) {
            return null;
        }
        byte[] bytes = null;
        final String key = normalizeKey(entityKey);
        try {
            bytes = java.util.Base64.getDecoder().decode(key);
        } catch (Exception ignored) {
        }
        if (bytes == null) {
            try {
                bytes = java.util.Base64.getUrlDecoder().decode(key);
            } catch (Exception ignored) {
                return null;
            }
        }
        final List<Field> fields = Field.parse(bytes);
        if (fields == null) {
            return null;
        }
        final Field id = Field.find(fields, 2, Field.WIRE_LENGTH_DELIMITED);
        if (id == null || id.bytes == null || id.bytes.length != 11) {
            return null;
        }
        return new String(id.bytes, StandardCharsets.US_ASCII);
    }

    @Nullable
    private static JSONObject findPlaylistRenderer(JSONObject response) {
        try {
            final JSONArray sections = response.getJSONObject("contents")
                    .getJSONObject("singleColumnBrowseResultsRenderer")
                    .getJSONArray("tabs").getJSONObject(0)
                    .getJSONObject("tabRenderer").getJSONObject("content")
                    .getJSONObject("sectionListRenderer").getJSONArray("contents");
            for (int i = 0, n = sections.length(); i < n; i++) {
                final JSONObject section = sections.optJSONObject(i);
                if (section == null) {
                    continue;
                }
                JSONObject list = section.optJSONObject("playlistVideoListRenderer");
                if (list != null) {
                    return list;
                }
                final JSONObject itemSection = section.optJSONObject("itemSectionRenderer");
                final JSONArray items = itemSection == null ? null : itemSection.optJSONArray("contents");
                if (items != null) {
                    for (int j = 0, m = items.length(); j < m; j++) {
                        final JSONObject item = items.optJSONObject(j);
                        list = item == null ? null : item.optJSONObject("playlistVideoListRenderer");
                        if (list != null) {
                            return list;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printDebug(() -> "Server playlist progress: no playlist video list in the response: "
                    + truncate(response.toString(), 300));
        }
        return null;
    }

    @Nullable
    private static JSONObject send(byte[] body, Map<String, String> headers) throws Exception {
        final HttpURLConnection connection = PlaylistRoutes.getConnection(BROWSE_PLAYLIST_PROGRESS, headers);
        connection.setFixedLengthStreamingMode(body.length);
        connection.getOutputStream().write(body);
        final int responseCode = connection.getResponseCode();
        if (responseCode == Requester.HTTP_STATUS_CODE_SUCCESS) {
            return Requester.parseJSONObjectAndDisconnect(connection);
        }
        Logger.printDebug(() -> "Server playlist progress: playlist page request failed with code " + responseCode);
        connection.disconnect();
        return null;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
