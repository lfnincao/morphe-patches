/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import java.util.List;

import app.morphe.extension.shared.innertube.PlayerResponseOuterClass.PlayerResponse;
import app.morphe.extension.youtube.patches.utils.ProtoNode;

/**
 * Extracts a watch-history resume position from a player response protobuf.
 * <p>
 * The primary signal is {@code playerConfig.playbackStartConfig} ({@code startSeconds} /
 * {@code startPosition.streamTimeMillis}), as seen in public player JSON fixtures (e.g. MediaServiceCore).
 * Field numbers for nested player config messages are not fully mapped in Morphe, so extraction uses
 * {@link ProtoNode} heuristics and ignores unknown or invalid values.
 */
public final class ServerResumePositionParser {

    /** Ignore resume hints below this (matches {@link RememberPlaybackPositionPatch}). */
    static final long MIN_RESUME_MS = 10_000L;

    /** Unix timestamps in {@code liveUtcStartSeconds} are larger than any VOD resume offset. */
    private static final long LIVE_UTC_SECONDS_THRESHOLD = 1_000_000_000L;

    private static final int PLAYER_RESPONSE_PLAYER_CONFIG_FIELD = 15;

    private ServerResumePositionParser() {
    }

    public static final class ParseResult {
        @Nullable
        public final Long resumePositionMs;
        @Nullable
        public final String candidateFieldPath;

        ParseResult(@Nullable Long resumePositionMs, @Nullable String candidateFieldPath) {
            this.resumePositionMs = resumePositionMs;
            this.candidateFieldPath = candidateFieldPath;
        }
    }

    /**
     * @return Resume position in milliseconds, or {@code null} if none was found or values are unusable.
     */
    @Nullable
    public static Long parseResumePositionMs(byte[] playerResponseBytes) {
        return parse(playerResponseBytes).resumePositionMs;
    }

    public static ParseResult parse(byte[] playerResponseBytes) {
        if (playerResponseBytes == null || playerResponseBytes.length == 0) {
            return new ParseResult(null, null);
        }

        try {
            PlayerResponse playerResponse = PlayerResponse.parseFrom(playerResponseBytes);
            if (playerResponse.hasPlayerConfig()) {
                byte[] playerConfigBytes = playerResponse.getPlayerConfig().toByteString().toByteArray();
                SearchResult fromConfig = findResumeInMessage(playerConfigBytes,
                        String.valueOf(PLAYER_RESPONSE_PLAYER_CONFIG_FIELD));
                if (fromConfig != null) {
                    return new ParseResult(fromConfig.positionMs, fromConfig.fieldPath);
                }
            }
        } catch (Exception ignored) {
            // Fall back to schema-less parsing below.
        }

        List<ProtoNode> root = ProtoNode.parse(playerResponseBytes);
        if (root == null) {
            return new ParseResult(null, null);
        }
        SearchResult fromRoot = findResumeDepthFirst(root, "");
        return new ParseResult(
                fromRoot != null ? fromRoot.positionMs : null,
                fromRoot != null ? fromRoot.fieldPath : null);
    }

    @Nullable
    private static SearchResult findResumeDepthFirst(List<ProtoNode> nodes, String pathPrefix) {
        for (ProtoNode node : nodes) {
            final String path = pathPrefix.isEmpty()
                    ? String.valueOf(node.getFieldNumber())
                    : pathPrefix + "." + node.getFieldNumber();
            if (node.children != null) {
                for (ProtoNode child : node.children) {
                    if (child.children == null) {
                        continue;
                    }
                    if (!hasStartSecondsVarint(child.children)) {
                        continue;
                    }
                    Long fromChild = parsePlaybackStartConfigFields(child.children);
                    if (fromChild != null) {
                        final String childPath = path + "." + child.getFieldNumber();
                        return new SearchResult(fromChild, childPath);
                    }
                }
                Long fromSelf = parsePlaybackStartConfigFields(node.children);
                if (fromSelf != null && hasStartSecondsVarint(node.children)) {
                    return new SearchResult(fromSelf, path);
                }
                SearchResult nested = findResumeDepthFirst(node.children, path);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    @Nullable
    private static SearchResult findResumeInMessage(byte[] messageBytes, String pathPrefix) {
        List<ProtoNode> nodes = ProtoNode.parse(messageBytes);
        if (nodes == null) {
            return null;
        }
        return findResumeDepthFirst(nodes, pathPrefix);
    }

    /**
     * Recognizes a {@code playbackStartConfig}-shaped message: a varint {@code startSeconds} and/or
     * a nested message whose text field is {@code streamTimeMillis}.
     */
    @Nullable
    static Long parsePlaybackStartConfigFields(List<ProtoNode> fields) {
        Long startSeconds = null;
        Long streamTimeMillis = null;

        for (ProtoNode field : fields) {
            String directText = field.decodeText();
            if (directText != null && isDigits(directText)) {
                try {
                    long millis = Long.parseLong(directText);
                    if (millis > 0) {
                        streamTimeMillis = millis;
                    }
                } catch (NumberFormatException ignored) {
                    // Not streamTimeMillis.
                }
            }
            if (field.children != null) {
                for (ProtoNode child : field.children) {
                    String text = child.decodeText();
                    if (text != null && isDigits(text)) {
                        try {
                            long millis = Long.parseLong(text);
                            if (millis > 0) {
                                streamTimeMillis = millis;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not streamTimeMillis.
                        }
                    }
                }
            }
            Long varint = field.getVarint();
            if (varint != null && varint > 0 && varint < LIVE_UTC_SECONDS_THRESHOLD) {
                startSeconds = varint;
            }
        }

        if (streamTimeMillis != null) {
            if (streamTimeMillis < MIN_RESUME_MS) {
                return null;
            }
            if (startSeconds != null) {
                long expectedMs = startSeconds * 1000L;
                if (Math.abs(expectedMs - streamTimeMillis) > 5_000L) {
                    // Prefer the more precise millis field when both disagree.
                    return streamTimeMillis;
                }
            }
            return streamTimeMillis;
        }

        if (startSeconds != null) {
            long millis = startSeconds * 1000L;
            return millis >= MIN_RESUME_MS ? millis : null;
        }
        return null;
    }

    /** {@code playbackStartConfig} carries {@code startSeconds}; nested {@code startPosition} alone does not. */
    private static boolean hasStartSecondsVarint(List<ProtoNode> fields) {
        for (ProtoNode field : fields) {
            Long varint = field.getVarint();
            if (varint != null && varint > 0 && varint < LIVE_UTC_SECONDS_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class SearchResult {
        final long positionMs;
        final String fieldPath;

        SearchResult(long positionMs, String fieldPath) {
            this.positionMs = positionMs;
            this.fieldPath = fieldPath;
        }
    }
}