/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 */

package app.morphe.extension.youtube.patches.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.protobuf.CodedOutputStream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import app.morphe.extension.youtube.patches.utils.ProtoNode;

public class ServerResumePositionParserTest {

    @Test
    public void parsePlaybackStartConfigFields_prefersStreamTimeMillis() {
        List<ProtoNode> fields = ProtoNode.parse(buildPlaybackStartConfig(318, "318000"));
        assertEquals(Long.valueOf(318_000L), ServerResumePositionParser.parsePlaybackStartConfigFields(fields));
    }

    @Test
    public void parsePlaybackStartConfigFields_startSecondsOnly() {
        List<ProtoNode> fields = ProtoNode.parse(buildPlaybackStartConfig(45, null));
        assertEquals(Long.valueOf(45_000L), ServerResumePositionParser.parsePlaybackStartConfigFields(fields));
    }

    @Test
    public void parsePlaybackStartConfigFields_ignoresShortResume() {
        List<ProtoNode> fields = ProtoNode.parse(buildPlaybackStartConfig(5, "5000"));
        assertNull(ServerResumePositionParser.parsePlaybackStartConfigFields(fields));
    }

    @Test
    public void parseResumePositionMs_nestedInPlayerResponse() throws IOException {
        byte[] playerConfig = buildPlaybackStartConfig(120, "120000");
        byte[] playerResponse = wrapField(15, playerConfig);
        assertEquals(Long.valueOf(120_000L), ServerResumePositionParser.parseResumePositionMs(playerResponse));
    }

  private static byte[] buildPlaybackStartConfig(long startSeconds, String streamTimeMillis) {
        try {
            ByteArrayOutputStream inner = new ByteArrayOutputStream();
            CodedOutputStream out = CodedOutputStream.newInstance(inner);
            if (streamTimeMillis != null) {
                byte[] startPosition = wrapStringField(1, streamTimeMillis);
                out.writeMessage(2, com.google.protobuf.ByteString.copyFrom(startPosition));
            }
            out.writeUInt64(1, startSeconds);
            out.flush();
            return inner.toByteArray();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static byte[] wrapStringField(int fieldNumber, String value) throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(message);
        out.writeString(fieldNumber, value);
        out.flush();
        return message.toByteArray();
    }

    private static byte[] wrapField(int fieldNumber, byte[] payload) throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(message);
        out.writeMessage(fieldNumber, com.google.protobuf.ByteString.copyFrom(payload));
        out.flush();
        return message.toByteArray();
    }
}