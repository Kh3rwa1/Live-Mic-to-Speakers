package com.word.way.activity;

import android.content.Context;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.word.way.R;
import com.word.way.audio.LiveAudioFailure;
import demo.ads.AdsHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class LiveMicrophoneFeedbackTest {
    @Before public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app); AdsHandler.setAdsOn(false);
    }
    @Test public void eachFailureHasPersistentRecoveryWithoutStartingCapture() {
        LiveAudioFailure.Reason[] reasons = {LiveAudioFailure.Reason.PERMISSION, LiveAudioFailure.Reason.SERVICE_UNAVAILABLE,
                LiveAudioFailure.Reason.FOCUS_UNAVAILABLE, LiveAudioFailure.Reason.UNSUPPORTED_CONFIGURATION,
                LiveAudioFailure.Reason.MICROPHONE_UNAVAILABLE, LiveAudioFailure.Reason.READ_FAILED,
                LiveAudioFailure.Reason.OUTPUT_FAILED, LiveAudioFailure.Reason.CANCELLED, LiveAudioFailure.Reason.UNKNOWN};
        int[] messages = {R.string.live_error_permission, R.string.live_error_service, R.string.live_error_focus,
                R.string.live_error_configuration, R.string.live_error_microphone, R.string.live_error_input,
                R.string.live_error_output, R.string.live_error_cancelled, R.string.quality_mic_error};
        try (ActivityScenario<LiveMicrophoneActivity> scenario = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            for (int i = 0; i < reasons.length; i++) {
                final int index = i;
                scenario.onActivity(activity -> {
                    activity.showAudioError(new LiveAudioFailure(reasons[index], "diagnostic text must not become UI copy"));
                    assertEquals(activity.getString(messages[index]), ((TextView) activity.findViewById(R.id.studio_feedback)).getText().toString());
                    assertEquals(activity.getString(R.string.quality_mic_off), ((TextView) activity.findViewById(R.id.tv_start_stop_new)).getText().toString());
                    assertEquals(activity.getString(R.string.quality_start_microphone), activity.findViewById(R.id.iv_start_stop_new).getContentDescription().toString());
                });
            }
        }
    }
}
