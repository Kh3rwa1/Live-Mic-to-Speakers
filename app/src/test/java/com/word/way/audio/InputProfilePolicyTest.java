package com.word.way.audio;

import android.media.MediaRecorder;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputProfilePolicyTest {

    @Test
    public void lowLatency_onApi29Plus_prefersVoicePerformanceThenRecognitionThenMic() {
        int[] sources = InputProfilePolicy.candidateSources(29, InputProfilePolicy.PROFILE_LOW_LATENCY);
        assertArrayEquals(new int[]{
                MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
        }, sources);

        assertFalse(InputProfilePolicy.isAecRequested(InputProfilePolicy.PROFILE_LOW_LATENCY));
        assertFalse(InputProfilePolicy.isNsRequested(InputProfilePolicy.PROFILE_LOW_LATENCY));
    }

    @Test
    public void lowLatency_belowApi29_omitsVoicePerformance() {
        int[] sources = InputProfilePolicy.candidateSources(28, InputProfilePolicy.PROFILE_LOW_LATENCY);
        assertArrayEquals(new int[]{
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
        }, sources);
    }

    @Test
    public void balancedProfile_usesMicAndRecognition_enablesNsOnly() {
        int[] sources = InputProfilePolicy.candidateSources(34, InputProfilePolicy.PROFILE_BALANCED);
        assertArrayEquals(new int[]{
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
        }, sources);

        assertFalse(InputProfilePolicy.isAecRequested(InputProfilePolicy.PROFILE_BALANCED));
        assertTrue(InputProfilePolicy.isNsRequested(InputProfilePolicy.PROFILE_BALANCED));
    }

    @Test
    public void noisyRoomProfile_usesVoiceCommunication_enablesAecAndNs() {
        int[] sources = InputProfilePolicy.candidateSources(34, InputProfilePolicy.PROFILE_NOISY_ROOM);
        assertArrayEquals(new int[]{
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
        }, sources);

        assertTrue(InputProfilePolicy.isAecRequested(InputProfilePolicy.PROFILE_NOISY_ROOM));
        assertTrue(InputProfilePolicy.isNsRequested(InputProfilePolicy.PROFILE_NOISY_ROOM));
    }

    @Test
    public void sourceNameFormatting_mapsKnownSources() {
        assertEquals("VOICE_PERFORMANCE (10)", InputProfilePolicy.sourceName(MediaRecorder.AudioSource.VOICE_PERFORMANCE));
        assertEquals("VOICE_RECOGNITION (6)", InputProfilePolicy.sourceName(MediaRecorder.AudioSource.VOICE_RECOGNITION));
        assertEquals("MIC (1)", InputProfilePolicy.sourceName(MediaRecorder.AudioSource.MIC));
        assertEquals("VOICE_COMMUNICATION (7)", InputProfilePolicy.sourceName(MediaRecorder.AudioSource.VOICE_COMMUNICATION));
    }

    @Test
    public void outputUsage_mapsProfileToAppropriateAudioAttributesUsage() {
        assertEquals(android.media.AudioAttributes.USAGE_MEDIA,
                InputProfilePolicy.outputUsage(InputProfilePolicy.PROFILE_LOW_LATENCY));
        assertEquals(android.media.AudioAttributes.USAGE_MEDIA,
                InputProfilePolicy.outputUsage(InputProfilePolicy.PROFILE_BALANCED));
        assertEquals(android.media.AudioAttributes.USAGE_MEDIA,
                InputProfilePolicy.outputUsage(InputProfilePolicy.PROFILE_NOISY_ROOM));
    }

    @Test
    public void builtinSpeaker_alwaysSelectsVoiceCommunicationAndEnablesAecAndNs() {
        for (int profile : new int[]{InputProfilePolicy.PROFILE_LOW_LATENCY, InputProfilePolicy.PROFILE_BALANCED, InputProfilePolicy.PROFILE_NOISY_ROOM}) {
            int[] sources = InputProfilePolicy.candidateSources(34, profile, true);
            assertArrayEquals(new int[]{
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC
            }, sources);

            assertTrue(InputProfilePolicy.isAecRequested(profile, true));
            assertTrue(InputProfilePolicy.isNsRequested(profile, true));
        }
    }

    @Test
    public void nonBuiltinSpeaker_preservesProfileSpecificSourcesAndEffects() {
        int[] lowLatencySources = InputProfilePolicy.candidateSources(29, InputProfilePolicy.PROFILE_LOW_LATENCY, false);
        assertArrayEquals(new int[]{
                MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
        }, lowLatencySources);
        assertFalse(InputProfilePolicy.isAecRequested(InputProfilePolicy.PROFILE_LOW_LATENCY, false));
        assertFalse(InputProfilePolicy.isNsRequested(InputProfilePolicy.PROFILE_LOW_LATENCY, false));

        int[] balancedSources = InputProfilePolicy.candidateSources(34, InputProfilePolicy.PROFILE_BALANCED, false);
        assertArrayEquals(new int[]{
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
        }, balancedSources);
        assertFalse(InputProfilePolicy.isAecRequested(InputProfilePolicy.PROFILE_BALANCED, false));
        assertTrue(InputProfilePolicy.isNsRequested(InputProfilePolicy.PROFILE_BALANCED, false));
    }
}
