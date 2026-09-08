package com.word.way;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Environment;
import androidx.core.content.FileProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.word.way.player.RecordingShare;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecordingSharingTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final List<File> temporary = new ArrayList<>();
    private File recording(File directory) throws Exception {
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File file = File.createTempFile("share_test_", ".m4a", directory);
        temporary.add(file);
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(new byte[]{1, 2, 3}); }
        return file;
    }
    private Uri uri(File file) { return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file); }
    private void rejected(File file) {
        try { uri(file); fail("File outside recording roots must not receive a URI: " + file); }
        catch (IllegalArgumentException expected) { }
    }
    @After public void removeTestFiles() { for (File file : temporary) file.delete(); }
    @Test public void internalRecordingFoldersAreReadableThroughContentUris() throws Exception {
        for (String name : new String[]{"Recording", "HPRecording"}) {
            Uri shared = uri(recording(new File(context.getFilesDir(), name)));
            assertEquals("content", shared.getScheme());
            try (InputStream input = context.getContentResolver().openInputStream(shared)) {
                assertNotNull(input); assertEquals(1, input.read());
            }
        }
    }
    @Test public void externalAppRecordingFoldersAreAllowedButTheirSiblingsAreNot() throws Exception {
        File music = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        org.junit.Assume.assumeNotNull(music);
        for (String name : new String[]{"Recording", "HPRecording"}) assertEquals("content", uri(recording(new File(music, name))).getScheme());
        rejected(new File(music, "unrelated.m4a"));
    }
    @Test public void privateFilesAndTraversalAreRejected() {
        rejected(new File(context.getFilesDir(), "private.m4a"));
        rejected(new File(context.getFilesDir(), "Recording/../private.m4a"));
        rejected(new File(context.getCacheDir(), "private.m4a"));
    }
    @Test public void shareIntentGrantsReadOnlyAccessAndUsesMp4AudioMime() throws Exception {
        File file = recording(new File(context.getFilesDir(), "Recording"));
        Intent send = RecordingShare.createShareIntent(context, file);
        assertEquals(Intent.ACTION_SEND, send.getAction()); assertEquals("audio/mp4", send.getType());
        assertNotEquals(0, send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        assertEquals(0, send.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertNotNull(send.getClipData()); assertEquals(uri(file), send.getClipData().getItemAt(0).getUri());
        assertEquals(uri(file), (Uri) send.getParcelableExtra(Intent.EXTRA_STREAM));
    }
    @Test public void providerIsNotExported() {
        ProviderInfo provider = context.getPackageManager().resolveContentProvider(context.getPackageName() + ".provider", 0);
        assertNotNull(provider); assertFalse(provider.exported); assertTrue(provider.grantUriPermissions);
    }
    @Test public void adMeasurementInitializationIsDeferred() throws Exception {
        assertTrue(context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA)
                .metaData.getBoolean("com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT", false));
    }
}
