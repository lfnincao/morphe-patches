/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 */

package app.morphe.extension.youtube.patches.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

public class ServerPlaylistProgressTest {

    @Test
    public void readsVideoIdOfPositionEntityKey() {
        // videoPlaybackPositionToggleEntityKey of a playlistVideoRenderer resume overlay.
        assertEquals("trciDaGUugw", ServerPlaylistProgress.videoIdOfEntityKey("Egt0cmNpRGFHVXVndyDpAigB"));
        // URL encoded padding, as in entityBatchUpdate mutations.
        assertEquals("nhZavmFEe8o", ServerPlaylistProgress.videoIdOfEntityKey("EgtuaFphdm1GRWU4byDpAigB"));
    }

    @Test
    public void ignoresKeysWithoutVideoId() {
        assertNull(ServerPlaylistProgress.videoIdOfEntityKey(
                "EhxidXR0b24tdG91Y2gtZmVlZGJhY2stZW50aXR5IMsBKAE%3D"));
        assertNull(ServerPlaylistProgress.videoIdOfEntityKey(""));
        assertNull(ServerPlaylistProgress.videoIdOfEntityKey("not base64 !"));
    }

    @Test
    public void readsPositionOfEntity() throws Exception {
        assertEquals(Long.valueOf(754_000L), ServerPlaylistProgress.findPositionMs(new JSONObject(
                "{\"key\":\"Egt0cmNpRGFHVXVndyDpAigB\",\"videoId\":\"trciDaGUugw\","
                        + "\"lastPlaybackPositionSeconds\":\"754\"}")));
        assertEquals(Long.valueOf(1_234L), ServerPlaylistProgress.findPositionMs(new JSONObject(
                "{\"playbackPositionMs\":1234}")));
        assertNull(ServerPlaylistProgress.findPositionMs(new JSONObject(
                "{\"key\":\"x\",\"buttonTouchFeedbackEnabled\":true}")));
    }
}
