package demo.ads;

import com.google.android.gms.ads.AdRequest;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link AdTestDevices}.
 */
public class AdTestDevicesTest {

    @Test
    public void releaseBuildReturnsEmptyList() {
        List<String> devices = AdTestDevices.forBuild(false, "33BE2250B43518CCDA7DE426D04EE231");
        assertTrue("Release build must never activate test devices", devices.isEmpty());
    }

    @Test
    public void debugBuildIncludesEmulatorByDefault() {
        List<String> devices = AdTestDevices.forBuild(true, "");
        assertEquals(1, devices.size());
        assertEquals(AdRequest.DEVICE_ID_EMULATOR, devices.get(0));

        List<String> fromNull = AdTestDevices.forBuild(true, null);
        assertEquals(1, fromNull.size());
        assertEquals(AdRequest.DEVICE_ID_EMULATOR, fromNull.get(0));
    }

    @Test
    public void debugBuildIncludesConfiguredTestDevices() {
        String configured = " 33BE2250B43518CCDA7DE426D04EE231 , 44AF3360C54629DDEB8EF537E15FF342 ";
        List<String> devices = AdTestDevices.forBuild(true, configured);
        assertEquals(3, devices.size());
        assertEquals(AdRequest.DEVICE_ID_EMULATOR, devices.get(0));
        assertEquals("33BE2250B43518CCDA7DE426D04EE231", devices.get(1));
        assertEquals("44AF3360C54629DDEB8EF537E15FF342", devices.get(2));
    }

    @Test
    public void blankAndWhitespaceEntriesAreIgnored() {
        List<String> devices = AdTestDevices.forBuild(true, " , , \t, \n ");
        assertEquals(1, devices.size());
        assertEquals(AdRequest.DEVICE_ID_EMULATOR, devices.get(0));
    }

    @Test
    public void duplicateEntriesAreDeduplicated() {
        String configured = AdRequest.DEVICE_ID_EMULATOR + ", ABC, ABC, DEF";
        List<String> devices = AdTestDevices.forBuild(true, configured);
        assertEquals(3, devices.size());
        assertEquals(AdRequest.DEVICE_ID_EMULATOR, devices.get(0));
        assertEquals("ABC", devices.get(1));
        assertEquals("DEF", devices.get(2));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void returnedListIsUnmodifiable() {
        List<String> devices = AdTestDevices.forBuild(true, "ABC");
        devices.add("ILLEGAL");
    }
}
