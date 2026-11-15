/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.patches.playback.ServerResumePositionParser.Field;

/**
 * Debug-only structural dump of the app's {@code playerConfig} for server resume diagnostics.
 * Every line starts with "Server resume dump &lt;videoId&gt;:" so it can be found in an exported log.
 * <ul>
 *     <li>{@code top}: every top-level PlayerConfig field number and wire type.</li>
 *     <li>{@code psc}: every leaf of playbackStartConfig (field 60102073), with its field path.</li>
 *     <li>{@code time?}: any leaf in playerConfig whose value could be a time (milliseconds,
 *         seconds, float seconds, or digit strings), with its field path. Buffering configs are skipped.</li>
 * </ul>
 * Paths are relative to playerConfig, i.e. "60102073.6.1" is playerResponse.15.60102073.6.1.
 */
final class ServerResumePositionProtoDump {

    private static final int MAX_DEPTH = 8;
    private static final int MAX_PSC_LINES = 60;
    private static final int MAX_TIME_LINES = 60;

    /**
     * PlayerConfig messages that only hold buffering and media tuning values (exoPlayerConfig,
     * androidMedialibConfig, mediaCommonConfig). They fill the time scan with hundreds of
     * millisecond thresholds, so the scan skips them.
     */
    private static final Set<Integer> TIME_SCAN_SKIPPED_FIELDS = new java.util.HashSet<>(
            java.util.Arrays.asList(57825011, 78958444, 215771584));
    private static final int TOP_FIELDS_PER_LINE = 12;
    private static final int MAX_DUMPED_VIDEOS = 50;

    /** 1 second to 48 hours. */
    private static final long MIN_TIME_MS = 1_000L;
    private static final long MAX_TIME_MS = 48L * 60 * 60 * 1000;
    private static final long MIN_TIME_SECONDS = 5L;
    private static final long MAX_TIME_SECONDS = 48L * 60 * 60;

    private static final Set<String> dumpedVideoIds = new LinkedHashSet<>();

    private ServerResumePositionProtoDump() {
    }

    static void logOnce(String videoId, @Nullable byte[] playerConfig,
                        ServerResumePositionParser.ParseResult result) {
        if (!Logger.isDebugEnabled() || videoId == null || videoId.isEmpty()) {
            return;
        }
        synchronized (dumpedVideoIds) {
            if (!dumpedVideoIds.add(videoId)) {
                return;
            }
            if (dumpedVideoIds.size() > MAX_DUMPED_VIDEOS) {
                dumpedVideoIds.remove(dumpedVideoIds.iterator().next());
            }
        }

        final String prefix = "Server resume dump " + videoId + ": ";
        try {
            if (playerConfig == null || playerConfig.length == 0) {
                Logger.printDebug(() -> prefix + "app player response has no playerConfig");
                return;
            }
            final List<Field> fields = Field.parse(playerConfig);
            if (fields == null) {
                Logger.printDebug(() -> prefix + "playerConfig is not a valid message, bytes="
                        + playerConfig.length);
                return;
            }

            final String summary = "playerConfig bytes=" + playerConfig.length
                    + " fields=" + fields.size()
                    + " playbackStartConfig=" + (result.hasPlaybackStartConfig ? "present" : "missing")
                    + " parsed=" + (result.rawPositionMs == null ? "none"
                    : result.rawPositionMs + "ms at " + result.fieldPath)
                    + (result.rawPositionMs != null && result.resumePositionMs == null ? " (rejected)" : "");
            Logger.printDebug(() -> prefix + summary);

            // Top-level field numbers and wire types.
            StringBuilder top = new StringBuilder();
            int count = 0;
            for (Field field : fields) {
                if (count > 0) {
                    top.append(' ');
                }
                top.append(field.number).append(':').append(typeLabel(field));
                if (++count == TOP_FIELDS_PER_LINE) {
                    final String line = top.toString();
                    Logger.printDebug(() -> prefix + "top " + line);
                    top.setLength(0);
                    count = 0;
                }
            }
            if (count > 0) {
                final String line = top.toString();
                Logger.printDebug(() -> prefix + "top " + line);
            }

            // Full playbackStartConfig tree.
            final List<String> pscLines = new ArrayList<>();
            for (Field field : fields) {
                if (field.number == ServerResumePositionParser.PLAYER_CONFIG_PLAYBACK_START_CONFIG_FIELD) {
                    collectLeaves(field, String.valueOf(field.number), 0, pscLines, MAX_PSC_LINES, false);
                }
            }
            for (String line : pscLines) {
                Logger.printDebug(() -> prefix + "psc " + line);
            }

            // Plausible time values anywhere in playerConfig.
            final List<String> timeLines = new ArrayList<>();
            for (Field field : fields) {
                if (TIME_SCAN_SKIPPED_FIELDS.contains(field.number)) {
                    continue;
                }
                collectLeaves(field, String.valueOf(field.number), 0, timeLines, MAX_TIME_LINES, true);
            }
            for (String line : timeLines) {
                Logger.printDebug(() -> prefix + "time? " + line);
            }
            if (timeLines.size() >= MAX_TIME_LINES) {
                Logger.printDebug(() -> prefix + "time? (truncated at " + MAX_TIME_LINES + ")");
            }
        } catch (Exception ex) {
            Logger.printException(() -> prefix + "dump failure", ex);
        }
    }

    private static void collectLeaves(Field field, String path, int depth, List<String> out,
                                      int max, boolean onlyTimeLike) {
        if (out.size() >= max) {
            return;
        }
        if (field.wireType == Field.WIRE_LENGTH_DELIMITED) {
            final List<Field> children = depth < MAX_DEPTH ? asMessage(field.bytes) : null;
            if (children != null) {
                if (children.isEmpty() && !onlyTimeLike) {
                    out.add(path + " MESSAGE {}");
                }
                for (Field child : children) {
                    collectLeaves(child, path + "." + child.number, depth + 1, out, max, onlyTimeLike);
                    if (out.size() >= max) {
                        return;
                    }
                }
                return;
            }
            final String text = asText(field.bytes);
            if (onlyTimeLike) {
                if (text != null && text.matches("\\d{1,9}")) {
                    out.add(path + " STRING \"" + text + "\"");
                }
                return;
            }
            out.add(path + (text != null ? " STRING \"" + truncate(text) + "\""
                    : " BYTES len=" + (field.bytes == null ? 0 : field.bytes.length)));
            return;
        }

        final String value = formatScalar(field);
        if (!onlyTimeLike || isTimeLike(field)) {
            out.add(path + " " + value);
        }
    }

    private static boolean isTimeLike(Field field) {
        switch (field.wireType) {
            case Field.WIRE_VARINT:
            case Field.WIRE_FIXED64: {
                final long v = field.varint;
                return (v >= MIN_TIME_MS && v <= MAX_TIME_MS)
                        || (v >= MIN_TIME_SECONDS && v <= MAX_TIME_SECONDS);
            }
            case Field.WIRE_FIXED32: {
                final float f = Float.intBitsToFloat((int) field.varint);
                return f >= MIN_TIME_SECONDS && f <= MAX_TIME_SECONDS;
            }
            default:
                return false;
        }
    }

    private static String formatScalar(Field field) {
        switch (field.wireType) {
            case Field.WIRE_VARINT:
                return "VARINT " + field.varint;
            case Field.WIRE_FIXED64:
                return "FIXED64 " + field.varint + " (double " + Double.longBitsToDouble(field.varint) + ")";
            case Field.WIRE_FIXED32:
                return "FIXED32 " + field.varint + " (float "
                        + String.format(Locale.US, "%.3f", Float.intBitsToFloat((int) field.varint)) + ")";
            default:
                return "?";
        }
    }

    private static String typeLabel(Field field) {
        switch (field.wireType) {
            case Field.WIRE_VARINT:
                return "V";
            case Field.WIRE_FIXED64:
                return "F64";
            case Field.WIRE_FIXED32:
                return "F32";
            case Field.WIRE_LENGTH_DELIMITED:
                return asMessage(field.bytes) != null ? "M" : "B" + (field.bytes == null ? 0 : field.bytes.length);
            default:
                return "?";
        }
    }

    @Nullable
    private static List<Field> asMessage(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        // Printable text also parses as a message surprisingly often; prefer text for it.
        if (asText(bytes) != null) {
            return null;
        }
        return Field.parse(bytes);
    }

    @Nullable
    private static String asText(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        for (byte b : bytes) {
            final int c = b & 0xFF;
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                return null;
            }
        }
        final String text = new String(bytes, StandardCharsets.UTF_8);
        return text.indexOf('\uFFFD') >= 0 ? null : text;
    }

    private static String truncate(String text) {
        final String lower = text.toLowerCase(Locale.US);
        if (lower.startsWith("http") || text.length() > 64) {
            return text.length() > 24 ? text.substring(0, 24) + "...(len=" + text.length() + ")" : text;
        }
        return text;
    }
}
