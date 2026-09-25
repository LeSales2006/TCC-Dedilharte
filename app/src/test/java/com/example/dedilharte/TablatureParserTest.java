package com.example.dedilharte;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.dedilharte.data.TablatureParser;
import com.example.dedilharte.model.TablatureEvent;

import org.junit.Test;

import java.util.List;

public class TablatureParserTest {

    @Test
    public void parserPreservesColumnsAndGroupsChords() {
        String tab =
                "E|--0---10--|\n" +
                "B|--1-------|\n" +
                "G|----------|\n" +
                "D|----------|\n" +
                "A|--3-------|\n" +
                "E|----------|";

        List<TablatureEvent> events = TablatureParser.parse(tab, 120, 4);

        assertEquals(2, events.size());
        assertEquals(2, events.get(0).getColumn());
        assertTrue(events.get(0).isChord());
        assertEquals(3, events.get(0).getNotes().size());
        assertEquals("E4", events.get(0).getNotes().get(0).getMusicalNote());
        assertEquals("C4", events.get(0).getNotes().get(1).getMusicalNote());
        assertEquals("C3", events.get(0).getNotes().get(2).getMusicalNote());
        assertEquals(6, events.get(1).getColumn());
        assertEquals(10, events.get(1).getNotes().get(0).getFret());
        assertEquals("D5", events.get(1).getNotes().get(0).getMusicalNote());
    }

    @Test
    public void parserKeepsRepeatedNotesAsSeparateEvents() {
        String tab =
                "E|0---0-----|\n" +
                "B|----------|\n" +
                "G|----------|\n" +
                "D|----------|\n" +
                "A|----------|\n" +
                "E|----------|";

        List<TablatureEvent> events = TablatureParser.parse(tab);

        assertEquals(2, events.size());
        assertEquals(0, events.get(0).getColumn());
        assertEquals(4, events.get(1).getColumn());
    }

    @Test
    public void timingUsesColumnDistanceBpmAndColumnsPerBeat() {
        String tab =
                "E|0-------0-|\n" +
                "B|----------|\n" +
                "G|----------|\n" +
                "D|----------|\n" +
                "A|----------|\n" +
                "E|----------|";

        List<TablatureEvent> events = TablatureParser.parse(tab, 120, 4);

        assertEquals(0L, events.get(0).getInstantMs());
        assertEquals(1000L, events.get(1).getInstantMs());
    }

    @Test
    public void noteMappingUsesStandardGuitarTuning() {
        assertEquals("E4", TablatureParser.getNoteFromStringAndFret(0, 0));
        assertEquals("F4", TablatureParser.getNoteFromStringAndFret(0, 1));
        assertEquals("G4", TablatureParser.getNoteFromStringAndFret(0, 3));
        assertEquals("C4", TablatureParser.getNoteFromStringAndFret(1, 1));
        assertEquals("D4", TablatureParser.getNoteFromStringAndFret(1, 3));
        assertEquals("G2", TablatureParser.getNoteFromStringAndFret(5, 3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parserRejectsMissingStringLine() {
        TablatureParser.parse("E|0|\nB|0|\nG|0|\nD|0|\nA|0|");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parserRejectsInvalidCharacter() {
        TablatureParser.parse("E|x|\nB|-|\nG|-|\nD|-|\nA|-|\nE|-|");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parserRejectsEmptyTablature() {
        TablatureParser.parse("");
    }
}
