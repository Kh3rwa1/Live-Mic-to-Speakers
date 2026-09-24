package com.word.way.audio;

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
     * Ordered candidate audio sources based on API level and user profile preference.
     */
    public static int[] candidateSources(int apiLevel, int profile) {
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
        return profile == PROFILE_NOISY_ROOM;
    }

    public static boolean isNsRequested(int profile) {
        return profile == PROFILE_BALANCED || profile == PROFILE_NOISY_ROOM;
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
