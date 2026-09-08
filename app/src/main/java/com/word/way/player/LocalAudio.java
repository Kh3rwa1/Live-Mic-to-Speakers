package com.word.way.player;

import android.os.Parcel;
import android.os.Parcelable;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;


public final class LocalAudio implements Parcelable {
    public static final CREATOR CREATOR = new CREATOR(null);
    private String audioArtist;
    private long audioDuration;
    private long audioId;
    private String audioTitle;
    private String audioUri;
    private String dateModified;
    private boolean isHighlight;
    private boolean isPlay;

    public int describeContents() {
        return 0;
    }

    
    public static final class CREATOR implements Creator<LocalAudio> {
        public CREATOR(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private CREATOR() {
        }

        @Override 
        public LocalAudio createFromParcel(Parcel parcel) {
            Intrinsics.checkNotNullParameter(parcel, "parcel");
            return new LocalAudio(parcel);
        }

        @Override 
        public LocalAudio[] newArray(int i) {
            return new LocalAudio[i];
        }
    }

    public  long getAudioId() {
        return this.audioId;
    }

    public  String getAudioTitle() {
        return this.audioTitle;
    }

    public  long getAudioDuration() {
        return this.audioDuration;
    }

    public  String getAudioUri() {
        return this.audioUri;
    }

    public  String getDateModified() {
        return this.dateModified;
    }

    public  boolean isPlay() {
        return this.isPlay;
    }

    public  void setPlay(boolean z) {
        this.isPlay = z;
    }

    public  boolean isHighlight() {
        return this.isHighlight;
    }

    public  void setHighlight(boolean z) {
        this.isHighlight = z;
    }

    public LocalAudio(long j, String str, String str2, long j2, String str3, String str4) {
        this.audioId = j;
        this.audioTitle = str;
        this.audioArtist = str2;
        this.audioDuration = j2;
        this.audioUri = str3;
        this.dateModified = str4;
    }

    protected LocalAudio(Parcel parcel) {
        Intrinsics.checkNotNullParameter(parcel, "in");
        this.audioId = parcel.readLong();
        this.audioTitle = parcel.readString();
        this.audioArtist = parcel.readString();
        this.audioDuration = parcel.readLong();
        this.audioUri = parcel.readString();
        this.dateModified = parcel.readString();
        boolean z = false;
        this.isPlay = parcel.readByte() != 0;
        this.isHighlight = parcel.readByte() != 0 ? true : z;
    }

    public void writeToParcel(Parcel parcel, int i) {
        Intrinsics.checkNotNullParameter(parcel, "dest");
        parcel.writeLong(this.audioId);
        parcel.writeString(this.audioTitle);
        parcel.writeString(this.audioArtist);
        parcel.writeLong(this.audioDuration);
        parcel.writeString(this.audioUri);
        parcel.writeString(this.dateModified);
        parcel.writeByte(this.isPlay ? (byte) 1 : 0);
        parcel.writeByte(this.isHighlight ? (byte) 1 : 0);
    }
}
