package com.example.dedilharte;

import static org.junit.Assert.*;
import com.example.dedilharte.network.model.ArpeggioResponse;
import com.google.gson.Gson;
import org.junit.Test;

public class ArpeggioJsonTest {
    private final Gson gson = new Gson();

    @Test public void missingPublicationIsNotAssumedPublished() {
        ArpeggioResponse item = gson.fromJson(
                "{\"id\":\"a\",\"title\":\"Arpejo\",\"notes\":[{\"string\":1,\"fret\":0}]}", ArpeggioResponse.class);
        assertNull(item.active);
        assertFalse(item.isValid());
    }

    @Test public void malformedNotesCannotReplaceValidCache() {
        for (String notes : new String[]{"null", "[]", "[null]", "[{\"string\":7,\"fret\":0}]",
                "[{\"string\":0,\"fret\":0}]", "[{\"string\":1,\"fret\":-1}]"}) {
            ArpeggioResponse item = gson.fromJson("{\"id\":\"a\",\"title\":\"Arpejo\",\"active\":true,\"notes\":"
                    + notes + "}", ArpeggioResponse.class);
            assertFalse(item.isValid());
        }
        for (boolean active : new boolean[]{true, false}) {
            ArpeggioResponse item = gson.fromJson("{\"id\":\"a\",\"title\":\"Arpejo\",\"active\":" + active
                    + ",\"notes\":[{\"string\":1,\"fret\":0}]}", ArpeggioResponse.class);
            assertTrue(item.isValid());
        }
    }
}
