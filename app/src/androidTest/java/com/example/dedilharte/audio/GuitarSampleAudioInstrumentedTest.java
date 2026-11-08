package com.example.dedilharte.audio;

import static org.junit.Assert.*;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Decode the packaged recordings: a loaded player alone cannot prove that a note contains sound. */
public class GuitarSampleAudioInstrumentedTest {
    @Test public void openStringsContainAudibleAttacks() throws Exception {
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets();
        GuitarSampleBank bank;
        try (InputStreamReader manifest = new InputStreamReader(assets.open("guitar/manifest.json"), StandardCharsets.UTF_8);
             InputStreamReader sfz = new InputStreamReader(assets.open("guitar/meta-steel-guitar.sfz"), StandardCharsets.UTF_8)) {
            bank = new GuitarSampleBank(manifest, sfz);
        }
        for (int string = 0; string < 6; string++) {
            checkAttack(assets, bank.resolve(string, 0, 80).path);
        }
    }

    private void checkAttack(AssetManager assets, String path) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try (AssetFileDescriptor fd = assets.openFd(path)) {
            extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            extractor.selectTrack(0);
            MediaFormat format = extractor.getTrackFormat(0);
            int targetSamples = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    * format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) / 4;
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false, outputEnded = false;
            int count = 0, encoding = AudioFormat.ENCODING_PCM_16BIT;
            double peak = 0, squares = 0;
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (count < targetSamples && !outputEnded && SystemClock.uptimeMillis() < deadline) {
                if (!inputEnded) {
                    int input = decoder.dequeueInputBuffer(10000);
                    if (input >= 0) {
                        int size = extractor.readSampleData(decoder.getInputBuffer(input), 0);
                        inputEnded = size < 0;
                        decoder.queueInputBuffer(input, 0, Math.max(0, size), inputEnded ? 0 : extractor.getSampleTime(),
                                inputEnded ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        if (!inputEnded) extractor.advance();
                    }
                }
                int output = decoder.dequeueOutputBuffer(info, 10000);
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded = decoder.getOutputFormat();
                    if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = decoded.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (output >= 0) {
                    ByteBuffer pcm = decoder.getOutputBuffer(output).order(ByteOrder.nativeOrder());
                    pcm.position(info.offset); pcm.limit(info.offset + info.size);
                    int bytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
                    while (pcm.remaining() >= bytes && count < targetSamples) {
                        double value = encoding == AudioFormat.ENCODING_PCM_FLOAT ? pcm.getFloat() : pcm.getShort() / 32768d;
                        peak = Math.max(peak, Math.abs(value)); squares += value * value; count++;
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(output, false);
                }
            }
            Log.i("GuitarSampleAudioTest", path + " first250ms peakDb=" + 20 * Math.log10(peak)
                    + " rmsDb=" + 10 * Math.log10(squares / Math.max(1, count)));
            assertEquals(path + ": missing decoded frames", targetSamples, count);
            assertTrue(path + ": attack is silent or too quiet, peak=" + peak, peak > .005);
        } finally {
            if (decoder != null) decoder.release();
            extractor.release();
        }
    }
}
