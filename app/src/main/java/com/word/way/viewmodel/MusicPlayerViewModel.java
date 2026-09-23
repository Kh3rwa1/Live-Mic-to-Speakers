package com.word.way.viewmodel;

import android.net.Uri;
import androidx.lifecycle.ViewModel;
import java.io.File;

/**
 * Preserves MusicActivity playback state, metadata, and playlist across rotation.
 */
public class MusicPlayerViewModel extends ViewModel {
    private Uri track;
    private int position;
    private boolean autoplay = true;
    private File[] playlist = new File[0];
    private String title;
    private String artist;

    public Uri getTrack() {
        return track;
    }

    public void setTrack(Uri track) {
        this.track = track;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public void setAutoplay(boolean autoplay) {
        this.autoplay = autoplay;
    }

    public File[] getPlaylist() {
        return playlist;
    }

    public void setPlaylist(File[] playlist) {
        this.playlist = playlist != null ? playlist : new File[0];
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }
}
