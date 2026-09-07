package com.example.livemictospeaker.player;


public class SongListModel {
    String artist;
    String data;
    String displayName;
    String duration;
    String ids;
    String title;

    public SongListModel(String str, String str2, String str3, String str4, String str5, String str6) {
        this.ids = str;
        this.artist = str2;
        this.title = str3;
        this.data = str4;
        this.displayName = str5;
        this.duration = str6;
    }

    public String getId() {
        return this.ids;
    }

    public void setId(String str) {
        this.ids = str;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public String getData() {
        return this.data;
    }

    public void setData(String str) {
        this.data = str;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDuration() {
        return this.duration;
    }

    public void setDuration(String str) {
        this.duration = str;
    }
}
