package com.word.way.audio;

import com.word.way.player.LibraryStateChecks;
import org.junit.Test;

public final class LibraryQualityRegressionTest {
    @Test public void failuresKeepStableRecoveryCategories() { LiveAudioFailureChecks.runAll(); }
    @Test public void libraryStatesDistinguishEmptyFilteredAndFailedLoads() { LibraryStateChecks.runAll(); }
}
