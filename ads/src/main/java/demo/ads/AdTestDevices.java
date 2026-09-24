package demo.ads;

import com.google.android.gms.ads.AdRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves test device identifiers for AdMob, ensuring they are only activated in debuggable builds.
 */
public final class AdTestDevices {
    private AdTestDevices() { }

    /**
     * Resolves the list of test device IDs based on the build environment and configuration.
     *
     * @param debuggable true if the build is debuggable; release builds always receive an empty list.
     * @param configured comma-separated list of test device IDs (may be null, empty, or whitespace).
     * @return unmodifiable list containing {@link AdRequest#DEVICE_ID_EMULATOR} and valid configured IDs
     *         in debug builds, or an empty list in release builds.
     */
    public static List<String> forBuild(boolean debuggable, String configured) {
        if (!debuggable) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        list.add(AdRequest.DEVICE_ID_EMULATOR);
        if (configured != null && !configured.trim().isEmpty()) {
            for (String raw : configured.split(",")) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                    list.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableList(list);
    }
}
