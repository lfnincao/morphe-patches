/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.innertube.PlayerResponseOuterClass.PlayerResponse;
import app.morphe.extension.youtube.patches.utils.ProtoNode;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Debug-only structural dump of {@code playerConfig} for server resume diagnostics.
 */
final class ServerResumePositionProtoDump {

    private static final int MAX_LINES = 200;
    private static final int MAX_DEPTH = 10;
    private static final int MAX_STRING_CHARS = 64;
    private static final int PLAYER_RESPONSE_PLAYER_CONFIG_FIELD = 15;
    /** Known {@code PlayerConfig.mediaCommonConfig} field number from player_response.proto. */
    private static final int PLAYER_CONFIG_MEDIA_COMMON_FIELD = 215771584;
    /** {@link app.morphe.extension.shared.innertube.PlayerResponseOuterClass.MediaCommonConfig} */
    private static final int MEDIA_COMMON_SERVER_PLAYBACK_START_FIELD = 15;

    private static final Set<String> dumpedVideoIds = new HashSet<>();

    private ServerResumePositionProtoDump() {
    }

    static void logPlayerConfigOnce(String videoId, byte[] playerResponseBytes,
                                    @Nullable String candidateFieldPath) {
        if (!Settings.SERVER_RESUME_PLAYBACK_POSITION_EXPERIMENTAL.get() || !Logger.isDebugEnabled()) {
            return;
        }
        if (videoId == null || videoId.isEmpty() || playerResponseBytes == null) {
            return;
        }
        synchronized (dumpedVideoIds) {
            if (!dumpedVideoIds.add(videoId)) {
                return;
            }
        }

        final String prefix = "Server resume dump " + videoId + ": ";
        try {
            final byte[] playerConfig = extractPlayerConfigBytes(playerResponseBytes);
            if (playerConfig == null || playerConfig.length == 0) {
                Logger.printDebug(() -> prefix + "playerConfig missing");
                logCandidateLine(prefix, candidateFieldPath);
                return;
            }

            List<ProtoNode> fields = ProtoNode.parse(playerConfig);
            if (fields == null) {
                Logger.printDebug(() -> prefix + "playerConfig is not a valid message");
                logCandidateLine(prefix, candidateFieldPath);
                return;
            }

            final int[] lineCount = {0};
            Logger.printDebug(() -> prefix + "playerConfig bytes=" + playerConfig.length);
            dumpFields(fields, String.valueOf(PLAYER_RESPONSE_PLAYER_CONFIG_FIELD), 0, lineCount, prefix);

            final boolean hasMediaCommon = fields.stream()
                    .anyMatch(node -> node.getFieldNumber() == PLAYER_CONFIG_MEDIA_COMMON_FIELD);
            if (hasMediaCommon) {
                Logger.printDebug(() -> prefix + "note mediaCommonConfig field "
                        + PLAYER_CONFIG_MEDIA_COMMON_FIELD + " present");
            }
            logCandidateLine(prefix, candidateFieldPath);
        } catch (Exception ex) {
            Logger.printException(() -> prefix + "dump failure", ex);
        }
    }

    private static void logCandidateLine(String prefix, @Nullable String candidateFieldPath) {
        if (candidateFieldPath != null && !candidateFieldPath.isEmpty()) {
            Logger.printDebug(() -> prefix + "candidate=" + candidateFieldPath);
        } else {
            Logger.printDebug(() -> prefix + "candidate=no candidate");
        }
    }

    @Nullable
    private static byte[] extractPlayerConfigBytes(byte[] playerResponseBytes) {
        try {
            PlayerResponse playerResponse = PlayerResponse.parseFrom(playerResponseBytes);
            if (playerResponse.hasPlayerConfig()) {
                return playerResponse.getPlayerConfig().toByteString().toByteArray();
            }
        } catch (Exception ignored) {
            // Fall back to schema-less extraction.
        }

        List<ProtoNode> root = ProtoNode.parse(playerResponseBytes);
        if (root == null) {
            return null;
        }
        for (ProtoNode node : root) {
            if (node.getFieldNumber() == PLAYER_RESPONSE_PLAYER_CONFIG_FIELD) {
                return node.getPayload();
            }
        }
        return null;
    }

    private static void dumpFields(List<ProtoNode> fields, String pathPrefix, int depth, int[] lineCount,
                                   String logPrefix) {
        if (depth > MAX_DEPTH || lineCount[0] >= MAX_LINES) {
            return;
        }
        for (ProtoNode field : fields) {
            if (lineCount[0] >= MAX_LINES) {
                return;
            }
            final String path = pathPrefix + "." + field.getFieldNumber();
            final String wire = wireLabel(field);
            final String value = formatValue(field);
            final boolean highlight = isPlaybackStartCandidate(path, field);
            final String suffix = highlight ? " [playback-start candidate]" : "";
            Logger.printDebug(() -> logPrefix + path + " " + wire + " " + value + suffix);
            lineCount[0]++;

            if (field.children != null) {
                dumpFields(field.children, path, depth + 1, lineCount, logPrefix);
            }
        }
    }

    private static boolean isPlaybackStartCandidate(String path, ProtoNode field) {
        if (path.contains("." + PLAYER_CONFIG_MEDIA_COMMON_FIELD + "."
                + MEDIA_COMMON_SERVER_PLAYBACK_START_FIELD)) {
            return true;
        }
        if (field.children != null) {
            Long ms = ServerResumePositionParser.parsePlaybackStartConfigFields(field.children);
            return ms != null;
        }
        return false;
    }

    private static String wireLabel(ProtoNode field) {
        if (field.isText()) {
            return "STRING";
        }
        if (field.getVarint() != null) {
            return "VARINT";
        }
        if (field.children != null) {
            return "MESSAGE";
        }
        return "BYTES";
    }

    private static String formatValue(ProtoNode field) {
        if (field.isText()) {
            return quoteRedacted(field.getText());
        }
        final Long varint = field.getVarint();
        if (varint != null) {
            return Long.toString(varint);
        }
        if (field.children != null) {
            return "{" + field.children.size() + " fields}";
        }
        final byte[] payload = field.getPayload();
        return "bytes(len=" + (payload != null ? payload.length : 0) + ")";
    }

    private static String quoteRedacted(String text) {
        if (text == null) {
            return "\"\"";
        }
        final String lower = text.toLowerCase(Locale.US);
        if (lower.contains("http://") || lower.contains("https://") || looksLikeToken(text)) {
            return "\"<redacted>\"";
        }
        if (text.length() > MAX_STRING_CHARS) {
            return "\"" + text.substring(0, MAX_STRING_CHARS) + "...\"";
        }
        return "\"" + text + "\"";
    }

    private static boolean looksLikeToken(String text) {
        if (text.length() < 24) {
            return false;
        }
        final String lower = text.toLowerCase(Locale.US);
        return lower.contains("token") || lower.contains("cipher") || lower.contains("pot.");
    }
}