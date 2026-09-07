package com.example.livemictospeaker.player;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

/** User-initiated, read-only sharing. FileProvider enforces the recording-folder allowlist. */
public final class RecordingShare {
    private RecordingShare() {}
    public static Intent createShareIntent(Context context, File file) {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("Recording unavailable");
        String mime = AudioMimeTypes.forName(file.getName());
        if (mime == null) throw new IllegalArgumentException("Unsupported recording format");
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        Intent send = new Intent(Intent.ACTION_SEND).setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(context.getContentResolver(), "Audio recording", uri));
        return send;
    }
    public static void share(Context context, String path) {
        try {
            if (path == null) throw new IllegalArgumentException("No recording selected");
            Intent chooser = Intent.createChooser(createShareIntent(context, new File(path)), "Share recording");
            if (!(context instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(context, "No app is available to share this recording.", Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException | SecurityException error) {
            Toast.makeText(context, "Only this app's saved recordings can be shared. For older public-folder files, use your device's Files app.", Toast.LENGTH_LONG).show();
        }
    }
}
