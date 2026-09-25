package com.word.way.audio;

import android.media.AudioAttributes;
import android.media.MediaRecorder;

/**
 * Pure policy rules for input profile selection and hardware effect toggles.
 * Decoupled from Android runtime so selection logic is completely JVM-testable.
 */
public final class InputProfilePolicy {
    public static final int PROFILE_LOW_LATENCY = 0;
    public static final int PROFILE_BALANCED = 1;
    public static final int PROFILE_NOISY_ROOM = 2;

    private InputProfilePolicy() { }

    /**
     * Determines AudioAttributes usage based on input profile.
     *
     * <p>{@link AudioAttributes#USAGE_MEDIA} with {@link AudioAttributes#CONTENT_TYPE_SPEECH}
     * is used for all profiles to ensure output routes to the loudspeaker (or connected headphones/
     * Bluetooth) rather than the phone earpiece receiver (which is the default routing for
     * {@link AudioAttributes#USAGE_VOICE_COMMUNICATION} on telephony-capable devices).
     * Only the input source and hardware effects (AEC, NS) differ per profile.
     */
    public static int outputUsage(int profile) {
        return AudioAttributes.USAGE_MEDIA;
    }

    /**
     * Ordered candidate audio sources based on API level and user profile preference.
     */
    public static int[] candidateSources(int apiLevel, int profile) {
        return candidateSources(apiLevel, profile, false);
    }

    /**
     * Ordered candidate audio sources based on API level, user profile preference,
     * and whether audio is routing to the device's built-in loudspeaker.
     *
     * <p>When output is the built-in speaker, phone mic and speaker share the chassis
     * only centimeters apart. In this case, {@link MediaRecorder.AudioSource#VOICE_COMMUNICATION}
     * is required to activate platform hardware Acoustic Echo Cancellation (AEC), preventing
     * runaway howling loops regardless of the user profile.
     *
     * <p>When output is headphones, external speakers, or Bluetooth ({@code isBuiltinSpeaker == false}),
     * {@link #PROFILE_LOW_LATENCY} retains {@link MediaRecorder.AudioSource#VOICE_PERFORMANCE} on API 29+
     * to provide ultra-low latency without echo-cancellation filtering.
     */
    public static int[] candidateSources(int apiLevel, int profile, boolean isBuiltinSpeaker) {
        if (isBuiltinSpeaker) {
            return new int[]{
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC
            };
        }
        switch (profile) {
            case PROFILE_BALANCED:
                // Balanced: MIC (1) or VOICE_RECOGNITION (6)
                return new int[]{
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION
                };
            case PROFILE_NOISY_ROOM:
                // Noisy room / call-style: VOICE_COMMUNICATION (7) or MIC (1)
                return new int[]{
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.MIC
                };
            case PROFILE_LOW_LATENCY:
            default:
                // Low latency: VOICE_PERFORMANCE (10, API 29+), else VOICE_RECOGNITION (6), else MIC (1)
                if (apiLevel >= 29) {
                    return new int[]{
                            MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            MediaRecorder.AudioSource.MIC
                    };
                } else {
                    return new int[]{
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            MediaRecorder.AudioSource.MIC
                    };
                }
        }
    }

    public static boolean isAecRequested(int profile) {
        return isAecRequested(profile, false);
    }

    public static boolean isAecRequested(int profile, boolean isBuiltinSpeaker) {
        return isBuiltinSpeaker || profile == PROFILE_NOISY_ROOM;
    }

    public static boolean isNsRequested(int profile) {
        return isNsRequested(profile, false);
    }

    public static boolean isNsRequested(int profile, boolean isBuiltinSpeaker) {
        return isBuiltinSpeaker || profile == PROFILE_BALANCED || profile == PROFILE_NOISY_ROOM;
    }

    public static String sourceName(int source) {
        switch (source) {
            case MediaRecorder.AudioSource.VOICE_PERFORMANCE:
                return "VOICE_PERFORMANCE (10)";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION:
                return "VOICE_RECOGNITION (6)";
            case MediaRecorder.AudioSource.MIC:
                return "MIC (1)";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return "VOICE_COMMUNICATION (7)";
            case MediaRecorder.AudioSource.CAMCORDER:
                return "CAMCORDER (5)";
            case MediaRecorder.AudioSource.UNPROCESSED:
                return "UNPROCESSED (9)";
            default:
                return "SOURCE_" + source;
        }
    }

    public static String profileName(int profile) {
        switch (profile) {
            case PROFILE_BALANCED:
                return "Balanced";
            case PROFILE_NOISY_ROOM:
                return "Noisy room";
            case PROFILE_LOW_LATENCY:
            default:
                return "Low latency";
        }
    }
}
