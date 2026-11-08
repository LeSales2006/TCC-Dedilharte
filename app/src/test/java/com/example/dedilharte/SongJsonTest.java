package com.example.dedilharte;

import static org.junit.Assert.*;
import com.example.dedilharte.network.model.SongListResponse;
import com.example.dedilharte.network.model.SongResponse;
import com.google.gson.Gson;
import org.junit.Test;

public class SongJsonTest {
    private final Gson gson = new Gson();

    @Test public void canonicalAndLegacyJsonPreserveTempoOrderAndPublication() {
        for (String json : new String[]{
                "{\"id\":\"s\",\"title\":\"Song\",\"bpm\":92,\"sortOrder\":4,\"active\":true}",
                "{\"id\":\"s\",\"title\":\"Song\",\"recommendedBpm\":92,\"queueOrder\":4,\"status\":\"publicada\",\"inQueue\":true}",
                "{\"id\":\"s\",\"title\":\"Song\",\"recommended_bpm\":92,\"queue_order\":4,\"status\":\"publicada\",\"in_queue\":true}"}) {
            SongResponse song = gson.fromJson(json, SongResponse.class);
            assertTrue(song.isValid());
            assertTrue(song.isPublished());
            assertEquals(92, song.bpm);
            assertEquals(4, song.sortOrder);
        }
    }

    @Test public void missingItemsIsInvalidWhileExplicitEmptyArrayClearsCache() {
        assertNull(gson.fromJson("{}", SongListResponse.class).items);
        assertTrue(gson.fromJson("{\"items\":[]}", SongListResponse.class).items.isEmpty());
    }

    @Test public void absentPublicationDraftArchivedAndOffQueueNeverBecomePublished() {
        for (String publication : new String[]{"", ",\"active\":false", ",\"status\":\"rascunho\"",
                ",\"status\":\"arquivada\"", ",\"status\":\"publicada\",\"inQueue\":false"}) {
            SongResponse song = gson.fromJson("{\"id\":\"s\",\"title\":\"Song\"" + publication + "}", SongResponse.class);
            assertFalse(song.isPublished());
        }
    }
}
