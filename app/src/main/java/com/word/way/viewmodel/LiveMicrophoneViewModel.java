package com.word.way.viewmodel;

import androidx.lifecycle.ViewModel;

/**
 * Preserves live microphone UI state and user configurations (e.g., gain, route display)
 * across configuration changes (such as device rotation).
 */
public class LiveMicrophoneViewModel extends ViewModel {
    private float liveGain = 0.8f;
    private int lastPeak = 0;
    private String lastRoute = "";

    public float getLiveGain() {
        return liveGain;
    }

    public void setLiveGain(float gain) {
        this.liveGain = Math.max(0f, Math.min(1f, gain));
    }

    public int getLastPeak() {
        return lastPeak;
    }

    public void setLastPeak(int lastPeak) {
        this.lastPeak = lastPeak;
    }

    public String getLastRoute() {
        return lastRoute;
    }

    public void setLastRoute(String lastRoute) {
        this.lastRoute = lastRoute != null ? lastRoute : "";
    }

    private boolean running;
    private boolean starting;

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isStarting() {
        return starting;
    }

    public void setStarting(boolean starting) {
        this.starting = starting;
    }
}
