package com.word.way.audio;

import org.junit.Test;
import static org.junit.Assert.*;

/** JVM coverage for the native-rate/low-latency sizing rules used by AndroidAudioSession. */
public class AudioBufferSizingTest {
    @Test public void nativeRateLeadsAndFallbacksDeduplicate() {
        int[] rates = AudioBufferSizing.candidateRates(48000);
        assertArrayEquals(new int[]{48000, 44100, 16000, 8000}, rates);
    }
    @Test public void unknownNativeRateFallsBackToLegacyOrder() {
        assertArrayEquals(new int[]{48000, 44100, 16000, 8000}, AudioBufferSizing.candidateRates(0));
        assertArrayEquals(new int[]{48000, 44100, 16000, 8000}, AudioBufferSizing.candidateRates(-1));
    }
    @Test public void nonStandardNativeRateIsProbedFirstWithoutDuplication() {
        int[] rates = AudioBufferSizing.candidateRates(96000);
        assertArrayEquals(new int[]{96000, 48000, 44100, 16000, 8000}, rates);
    }
    @Test public void bufferNeverDropsBelowPlatformMinimum() {
        // A tiny burst must not shrink the buffer under the platform's safe minimum.
        assertEquals(4096, AudioBufferSizing.bufferBytes(4096, 16, 1, 2));
    }
    @Test public void bufferUsesSmallMultipleOfNativeBurstWhenLarger() {
        // 256-frame burst * 1 channel * 2 bytes * 2 = 1024 bytes, above the 512-byte minimum.
        assertEquals(1024, AudioBufferSizing.bufferBytes(512, 256, 1, 2));
    }
    @Test public void missingBurstFallsBackToMinimum() {
        assertEquals(2048, AudioBufferSizing.bufferBytes(2048, 0, 1, 2));
        assertEquals(2048, AudioBufferSizing.bufferBytes(2048, -1, 1, 2));
    }
    @Test public void invalidMinimumIsPassedThroughForCallerToReject() {
        assertEquals(0, AudioBufferSizing.bufferBytes(0, 128, 1, 2));
        assertEquals(-1, AudioBufferSizing.bufferBytes(-1, 128, 1, 2));
    }
    @Test public void scratchBufferFitsTheLargerDirection() {
        assertEquals(512, AudioBufferSizing.scratchSamples(1024, 512, 2));
        assertEquals(512, AudioBufferSizing.scratchSamples(512, 1024, 2));
    }
    @Test public void scratchBufferRoundsPartialSamplesUp() {
        assertEquals(3, AudioBufferSizing.scratchSamples(5, 0, 2));
        assertEquals(0, AudioBufferSizing.scratchSamples(0, 0, 2));
    }
}
