/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 */

package app.morphe.extension.youtube.patches.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.CodedOutputStream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ServerResumePositionParserTest {

    private static final int PSC = ServerResumePositionParser.PLAYER_CONFIG_PLAYBACK_START_CONFIG_FIELD;

    @Test
    public void readsStartPositionTimeMs() throws IOException {
        byte[] startPosition = concat(varint(1, 263_500L), varint(3, 42L));
        byte[] psc = message(6, startPosition);
        byte[] playerConfig = concat(varint(7, 1500L), message(PSC, psc), message(215771584, varint(1, 1L)));

        ServerResumePositionParser.ParseResult result = ServerResumePositionParser.parsePlayerConfig(playerConfig);
        assertTrue(result.hasPlaybackStartConfig);
        assertEquals(Long.valueOf(263_500L), result.resumePositionMs);
        assertEquals(PSC + ".6.1", result.fieldPath);
    }

    @Test
    public void appendsStartPosition() throws IOException {
        // Without a playback start config.
        byte[] playerConfig = concat(varint(7, 1500L), message(215771584, varint(1, 1L)));
        byte[] withStart = ServerResumePositionParser.withStartPosition(playerConfig, 740_000L);
        ServerResumePositionParser.ParseResult result = ServerResumePositionParser.parsePlayerConfig(withStart);
        assertTrue(result.hasPlaybackStartConfig);
        assertEquals(Long.valueOf(740_000L), result.resumePositionMs);
        assertEquals(PSC + ".6.1", result.fieldPath);

        // With a playback start config that has no start position: the appended one is a second
        // occurrence of the field, which protobuf merges into the first.
        byte[] playerConfig2 = concat(varint(7, 1500L), message(PSC, varint(4, 20_000L)));
        byte[] withStart2 = ServerResumePositionParser.withStartPosition(playerConfig2, 224_000L);
        byte[] expectedTail = message(PSC, message(6, varint(1, 224_000L)));
        assertEquals(playerConfig2.length + expectedTail.length, withStart2.length);
        for (int i = 0; i < expectedTail.length; i++) {
            assertEquals(expectedTail[i], withStart2[playerConfig2.length + i]);
        }
        com.google.protobuf.CodedInputStream input = com.google.protobuf.CodedInputStream.newInstance(withStart2);
        int fields = 0;
        while (true) {
            int tag = input.readTag();
            if (tag == 0) break;
            input.skipField(tag);
            fields++;
        }
        assertEquals(3, fields);
    }

    @Test
    public void fallsBackToStartSecondsFloat() throws IOException {
        byte[] psc = fixed32Float(1, 95.5f);
        ServerResumePositionParser.ParseResult result =
                ServerResumePositionParser.parsePlayerConfig(message(PSC, psc));
        assertEquals(Long.valueOf(95_500L), result.resumePositionMs);
        assertEquals(PSC + ".1", result.fieldPath);
    }

    @Test
    public void rejectsShortResume() throws IOException {
        byte[] psc = message(6, varint(1, 4_000L));
        ServerResumePositionParser.ParseResult result =
                ServerResumePositionParser.parsePlayerConfig(message(PSC, psc));
        assertNull(result.resumePositionMs);
        assertEquals(Long.valueOf(4_000L), result.rawPositionMs);
    }

    @Test
    public void noPlaybackStartConfig() throws IOException {
        // Values in other fields that look like times must not be used.
        byte[] playerConfig = concat(varint(4, 14L), message(2, varint(4, 939_933L)));
        ServerResumePositionParser.ParseResult result = ServerResumePositionParser.parsePlayerConfig(playerConfig);
        assertFalse(result.hasPlaybackStartConfig);
        assertNull(result.resumePositionMs);
        assertNull(result.rawPositionMs);
    }

    @Test
    public void malformedAndEmptyInput() {
        assertNull(ServerResumePositionParser.parsePlayerConfig(null).resumePositionMs);
        assertNull(ServerResumePositionParser.parsePlayerConfig(new byte[0]).resumePositionMs);
        assertNull(ServerResumePositionParser.parsePlayerConfig(new byte[]{-5, -56, 40, 57}).resumePositionMs);
    }

    private static byte[] varint(int field, long value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CodedOutputStream coded = CodedOutputStream.newInstance(out);
        coded.writeInt64(field, value);
        coded.flush();
        return out.toByteArray();
    }

    private static byte[] fixed32Float(int field, float value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CodedOutputStream coded = CodedOutputStream.newInstance(out);
        coded.writeFloat(field, value);
        coded.flush();
        return out.toByteArray();
    }

    private static byte[] message(int field, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CodedOutputStream coded = CodedOutputStream.newInstance(out);
        coded.writeByteArray(field, payload);
        coded.flush();
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }
}
