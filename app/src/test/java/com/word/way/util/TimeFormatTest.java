package com.word.way.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimeFormatTest {

    @Test
    public void formatsZeroAndNegativeAsZeros() {
        assertEquals("00:00", TimeFormat.formatDuration(0));
        assertEquals("00:00", TimeFormat.formatDuration(-1));
        assertEquals("00:00", TimeFormat.formatDuration(-5000));
    }

    @Test
    public void formatsSecondsUnderOneMinute() {
        assertEquals("00:01", TimeFormat.formatDuration(1000));
        assertEquals("00:09", TimeFormat.formatDuration(9000));
        assertEquals("00:59", TimeFormat.formatDuration(59000));
    }

    @Test
    public void formatsMinutesUnderOneHour() {
        assertEquals("01:00", TimeFormat.formatDuration(60000));
        assertEquals("01:05", TimeFormat.formatDuration(65000));
        assertEquals("10:30", TimeFormat.formatDuration(630000));
        assertEquals("59:59", TimeFormat.formatDuration(3599000));
    }

    @Test
    public void formatsHoursMinutesSeconds() {
        assertEquals("01:00:00", TimeFormat.formatDuration(3600000));
        assertEquals("01:02:03", TimeFormat.formatDuration(3723000));
        assertEquals("25:00:00", TimeFormat.formatDuration(90000000));
    }
}
