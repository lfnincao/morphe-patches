/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.playback;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracts YouTube's watch-history resume position from the app's {@code PlayerConfig} proto
 * (field 15 of the /player response).
 * <p>
 * Field numbers were read off YouTube 21.30.216, where {@code PlaybackStartPositionResolver}
 * resolves the start position from {@code playerConfig.playbackStartConfig.startPosition}:
 * <pre>
 * PlayerConfig        { PlaybackStartConfig playbackStartConfig = 60102073; }
 * PlaybackStartConfig { float startSeconds = 1; StartPosition startPosition = 6; ... }
 * StartPosition       { int64 timeMs = 1; int64 ? = 2; int64 relativeFreshnessSeconds = 3; }
 * </pre>
 * The resolver returns {@code startPosition.timeMs} directly as the playback start in milliseconds.
 */
public final class ServerResumePositionParser {

    /** Ignore resume hints below this (matches {@link RememberPlaybackPositionPatch}). */
    static final long MIN_RESUME_MS = 10_000L;

    /** Anything above this is not a VOD resume offset (48 hours). */
    static final long MAX_RESUME_MS = 48L * 60 * 60 * 1000;

    static final int PLAYER_CONFIG_PLAYBACK_START_CONFIG_FIELD = 60102073;
    static final int PLAYBACK_START_CONFIG_START_SECONDS_FIELD = 1;
    static final int PLAYBACK_START_CONFIG_START_POSITION_FIELD = 6;
    static final int START_POSITION_TIME_MS_FIELD = 1;

    static final String START_POSITION_PATH = PLAYER_CONFIG_PLAYBACK_START_CONFIG_FIELD + "."
            + PLAYBACK_START_CONFIG_START_POSITION_FIELD + "." + START_POSITION_TIME_MS_FIELD;
    static final String START_SECONDS_PATH = PLAYER_CONFIG_PLAYBACK_START_CONFIG_FIELD + "."
            + PLAYBACK_START_CONFIG_START_SECONDS_FIELD;

    private ServerResumePositionParser() {
    }

    public static final class ParseResult {
        /** Resume position, or null if none or below {@link #MIN_RESUME_MS}. */
        @Nullable
        public final Long resumePositionMs;
        /** Raw value found, even if it was rejected. */
        @Nullable
        public final Long rawPositionMs;
        @Nullable
        public final String fieldPath;
        public final boolean hasPlaybackStartConfig;

        ParseResult(@Nullable Long resumePositionMs, @Nullable Long rawPositionMs,
                    @Nullable String fieldPath, boolean hasPlaybackStartConfig) {
            this.resumePositionMs = resumePositionMs;
            this.rawPositionMs = rawPositionMs;
            this.fieldPath = fieldPath;
            this.hasPlaybackStartConfig = hasPlaybackStartConfig;
        }
    }

    private static final ParseResult EMPTY = new ParseResult(null, null, null, false);

    /**
     * @param playerConfigBytes Serialized PlayerConfig (field 15 of the player response).
     */
    public static ParseResult parsePlayerConfig(@Nullable byte[] playerConfigBytes) {
        if (playerConfigBytes == null || playerConfigBytes.length == 0) {
            return EMPTY;
        }
        final List<Field> playerConfig = Field.parse(playerConfigBytes);
        if (playerConfig == null) {
            return EMPTY;
        }
        final Field playbackStartConfigField = Field.find(playerConfig, PLAYER_CONFIG_PLAYBACK_START_CONFIG_FIELD,
                Field.WIRE_LENGTH_DELIMITED);
        if (playbackStartConfigField == null) {
            return EMPTY;
        }
        final List<Field> playbackStartConfig = Field.parse(playbackStartConfigField.bytes);
        if (playbackStartConfig == null) {
            return new ParseResult(null, null, null, true);
        }

        Long rawMs = null;
        String path = null;

        final Field startPositionField = Field.find(playbackStartConfig, PLAYBACK_START_CONFIG_START_POSITION_FIELD,
                Field.WIRE_LENGTH_DELIMITED);
        if (startPositionField != null) {
            final List<Field> startPosition = Field.parse(startPositionField.bytes);
            if (startPosition != null) {
                final Field timeMs = Field.find(startPosition, START_POSITION_TIME_MS_FIELD, Field.WIRE_VARINT);
                if (timeMs != null) {
                    rawMs = timeMs.varint;
                    path = START_POSITION_PATH;
                }
            }
        }

        if (rawMs == null) {
            final Field startSeconds = Field.find(playbackStartConfig, PLAYBACK_START_CONFIG_START_SECONDS_FIELD,
                    Field.WIRE_FIXED32);
            if (startSeconds != null) {
                final float seconds = Float.intBitsToFloat((int) startSeconds.varint);
                if (!Float.isNaN(seconds) && !Float.isInfinite(seconds)) {
                    rawMs = (long) (seconds * 1000f);
                    path = START_SECONDS_PATH;
                }
            }
        }

        final Long resumeMs = rawMs != null && rawMs >= MIN_RESUME_MS && rawMs <= MAX_RESUME_MS
                ? rawMs
                : null;
        return new ParseResult(resumeMs, rawMs, path, true);
    }

    /**
     * Minimal schema-less protobuf wire reader. Returns null for malformed input.
     */
    static final class Field {
        static final int WIRE_VARINT = 0;
        static final int WIRE_FIXED64 = 1;
        static final int WIRE_LENGTH_DELIMITED = 2;
        static final int WIRE_FIXED32 = 5;

        final int number;
        final int wireType;
        /** Varint, fixed32 or fixed64 value. */
        final long varint;
        /** Payload of a length-delimited field. */
        @Nullable
        final byte[] bytes;

        private Field(int number, int wireType, long varint, @Nullable byte[] bytes) {
            this.number = number;
            this.wireType = wireType;
            this.varint = varint;
            this.bytes = bytes;
        }

        @Nullable
        static Field find(List<Field> fields, int number, int wireType) {
            Field last = null;
            for (Field field : fields) {
                // Protobuf semantics: the last occurrence of a non-repeated field wins.
                if (field.number == number && field.wireType == wireType) {
                    last = field;
                }
            }
            return last;
        }

        @Nullable
        static List<Field> parse(@Nullable byte[] data) {
            if (data == null) {
                return null;
            }
            if (data.length == 0) {
                return Collections.emptyList();
            }
            final List<Field> fields = new ArrayList<>();
            final int[] pos = {0};
            while (pos[0] < data.length) {
                final long tag = readVarint(data, pos);
                if (tag < 0) {
                    return null;
                }
                final int number = (int) (tag >>> 3);
                final int wireType = (int) (tag & 0x7);
                if (number <= 0 || number > 536_870_911) {
                    return null;
                }
                switch (wireType) {
                    case WIRE_VARINT: {
                        final long value = readVarint(data, pos);
                        if (value == Long.MIN_VALUE) {
                            return null;
                        }
                        fields.add(new Field(number, wireType, value, null));
                        break;
                    }
                    case WIRE_FIXED64: {
                        if (pos[0] + 8 > data.length) {
                            return null;
                        }
                        long value = 0;
                        for (int i = 7; i >= 0; i--) {
                            value = (value << 8) | (data[pos[0] + i] & 0xFFL);
                        }
                        pos[0] += 8;
                        fields.add(new Field(number, wireType, value, null));
                        break;
                    }
                    case WIRE_LENGTH_DELIMITED: {
                        final long length = readVarint(data, pos);
                        if (length < 0 || length > data.length - pos[0]) {
                            return null;
                        }
                        final byte[] payload = new byte[(int) length];
                        System.arraycopy(data, pos[0], payload, 0, (int) length);
                        pos[0] += (int) length;
                        fields.add(new Field(number, wireType, 0, payload));
                        break;
                    }
                    case WIRE_FIXED32: {
                        if (pos[0] + 4 > data.length) {
                            return null;
                        }
                        long value = 0;
                        for (int i = 3; i >= 0; i--) {
                            value = (value << 8) | (data[pos[0] + i] & 0xFFL);
                        }
                        pos[0] += 4;
                        fields.add(new Field(number, wireType, value, null));
                        break;
                    }
                    default:
                        // Groups (3/4) are not used by YouTube responses.
                        return null;
                }
            }
            return fields;
        }

        /**
         * @return The varint, or {@link Long#MIN_VALUE} if malformed. Tags are never negative,
         *         so callers reading a tag may also check for a negative value.
         */
        private static long readVarint(byte[] data, int[] pos) {
            long result = 0;
            int shift = 0;
            while (shift < 64) {
                if (pos[0] >= data.length) {
                    return Long.MIN_VALUE;
                }
                final byte b = data[pos[0]++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            return Long.MIN_VALUE;
        }
    }
}
