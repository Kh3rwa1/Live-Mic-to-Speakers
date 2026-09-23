package com.word.way.player;

import android.content.Context;
import com.word.way.R;
import java.text.DateFormat;
import java.util.Date;


public final class MediaPlayerUtils {
    public static final MediaPlayerUtils INSTANCE = new MediaPlayerUtils();

    private MediaPlayerUtils() {
    }

    
    
    public  String milliSecondsToTimer(long j) {
        String str;
        String str2;
        int i = (int) (j / 3600000);
        long j2 = j % 3600000;
        int i2 = ((int) j2) / 60000;
        int i3 = (int) ((j2 % 60000) / 1000);
        if (i > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(i);
            sb.append(':');
            str = sb.toString();
        } else {
            str = "";
        }
        if (i2 < 10) {
            str = str + '0';
        }
        if (i3 < 10) {
            StringBuilder sb2 = new StringBuilder();
            sb2.append('0');
            sb2.append(i3);
            str2 = sb2.toString();
        } else {
            str2 = "" + i3;
        }
        return str + i2 + ':' + str2;
    }


    public  String getTimeAgo(String str, Context context) {
        if (str == null || context == null) return null;
        long parseLong;
        try {
            parseLong = Long.parseLong(str);
        } catch (NumberFormatException notNumeric) {
            return null;
        }
        if (parseLong < 1000000000000L) {
            parseLong *= 1000;
        }
        long currentTimeMillis = System.currentTimeMillis();
        if (parseLong > currentTimeMillis || parseLong <= 0) {
            return null;
        }
        long j = currentTimeMillis - parseLong;
        if (j < 60000) {
            return context.getString(R.string.just_now);
        }
        if (j < 3000000) {
            int i = ((int) j) / 60000;
            return context.getResources().getQuantityString(R.plurals.min_count_string, i, Integer.valueOf(i));
        } else if (j < 86400000) {
            int i2 = ((int) j) / 3600000;
            return context.getResources().getQuantityString(R.plurals.hour_count_string, i2, Integer.valueOf(i2));
        } else {
            // Locale-correct short date (e.g. "Sep 10, 2026" / localized equivalent).
            return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(parseLong));
        }
    }
}
