package com.example.dedilharte.audio;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;

public class GuitarSampleBankTest {
    private final File assets = new File("src/main/assets");
    private GuitarSampleBank bank;

    @Before
    public void loadBank() throws Exception {
        try (java.io.Reader manifest = Files.newBufferedReader(new File(assets, "guitar/manifest.json").toPath(), StandardCharsets.UTF_8);
             java.io.Reader sfz = Files.newBufferedReader(new File(assets, "guitar/meta-steel-guitar.sfz").toPath(), StandardCharsets.UTF_8)) {
            bank = new GuitarSampleBank(manifest, sfz);
        }
    }

    @Test
    public void allStringsFretsAndVelocityBoundariesResolveToLocalFiles() {
        for (int string = 0; string < 6; string++) {
            for (int fret = 0; fret <= 24; fret++) {
                for (int velocity : new int[]{1, 48, 49, 96, 97, 127}) {
                    GuitarSampleBank.Sample sample = bank.resolve(string, fret, velocity);
                    assertTrue(sample.path, new File(assets, sample.path).isFile());
                    assertTrue(sample.rate >= .5f && sample.rate <= 2f);
                    assertTrue(sample.gain >= .708f && sample.gain <= 1f);
                    String layer = velocity <= 48 ? "Vel01" : velocity <= 96 ? "Vel02" : "Vel03";
                    assertTrue(sample.path.contains(layer));
                }
            }
        }
    }

    @Test
    public void samePitchKeepsItsStringTimbre() {
        GuitarSampleBank.Sample low = bank.resolve(5, 5, 80);
        GuitarSampleBank.Sample a = bank.resolve(4, 0, 80);
        assertTrue(low.path.endsWith("StringLowE_Vel02_A2_01.ogg"));
        assertTrue(a.path.endsWith("StringA_Vel02_A2_01.ogg"));
        assertEquals(1f, low.rate, .0001f);
        assertEquals(1f, a.rate, .0001f);
    }

    @Test
    public void missingB2UsesSfzPitchCenterAndUpperFretsStayPlayable() {
        GuitarSampleBank.Sample b = bank.resolve(5, 7, 80);
        assertTrue(b.path.endsWith("StringLowE_Vel02_As2_01.ogg"));
        assertEquals(Math.pow(2, 1d / 12), b.rate, .0001);
        GuitarSampleBank.Sample highest = bank.resolve(0, 24, 80);
        assertTrue(highest.path.endsWith("StringHighE_Vel02_C6_01.ogg"));
        assertEquals(Math.pow(2, 4d / 12), highest.rate, .0001);
    }
}
