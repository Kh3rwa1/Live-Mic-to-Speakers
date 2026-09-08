package com.word.way;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Verify the installed, merged manifest rather than trusting source declarations alone. */
@RunWith(AndroidJUnit4.class)
public class AppBuildPolicyTest {
    @Test public void modernTargetPreservesIdentityAndPrivateComponents() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        String name = app.getPackageName();
        assertEquals("com.word.way", name);
        ApplicationInfo application = app.getApplicationInfo();
        assertEquals("Do not silently drop Android 7 support", 24, application.minSdkVersion);
        assertTrue("Android 17 must remain targeted", application.targetSdkVersion >= 37);
        assertEquals("Recording backups must remain disabled", 0, application.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
        PackageInfo info = app.getPackageManager().getPackageInfo(name,
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS);
        if (info.activities != null) for (ActivityInfo activity : info.activities) {
            if ((activity.name.startsWith(name + ".") || activity.name.startsWith("demo.ads."))
                    && !activity.name.equals(name + ".activity.SplashActivity"))
                assertFalse("Internal activity became exported: " + activity.name, activity.exported);
        }
        if (info.services != null) for (ServiceInfo service : info.services) {
            if (service.name.startsWith(name + "."))
                assertFalse("Internal service became exported: " + service.name, service.exported);
        }
        boolean found = false;
        if (info.providers != null) for (ProviderInfo provider : info.providers) {
            if ((name + ".provider").equals(provider.authority)) {
                found = true;
                assertFalse("Recording provider became exported", provider.exported);
                assertTrue("Temporary read sharing must remain available", provider.grantUriPermissions);
            }
        }
        assertTrue("Recording provider is missing", found);
    }
}
