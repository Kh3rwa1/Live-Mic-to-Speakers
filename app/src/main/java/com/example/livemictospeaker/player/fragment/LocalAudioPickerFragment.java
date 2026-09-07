package com.example.livemictospeaker.player.fragment;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.player.LocalAudio;
import com.example.livemictospeaker.player.MediaPlayerUtils;
import com.example.livemictospeaker.player.adapter.AudioAdapter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import io.reactivex.functions.Function3;


public final class LocalAudioPickerFragment extends Fragment {
    public static final Companion Companion = new Companion(null);
    private long audioID;
    private TextView audioText;
    public TextView currentTimeTv;
    public AudioAdapter localAudioAdapter;
    public Handler mMediaHandler;
    private final LocalAudioPickerFragmentRunnable mMediaSeekRunnable = new LocalAudioPickerFragmentRunnable(this);
    public MediaPlayer mediaPlayer;
    private LinearLayout mediaPlayerLayout;
    private TextView noAudioTv;
    private ImageView playPause;
    public SeekBar playerSeekbar;
    private RecyclerView recyclerView;
    private final LocalAudioPickerFragmentsearchOnTextChangeListener searchOnTextChangeListener = new LocalAudioPickerFragmentsearchOnTextChangeListener(this);
    private SearchView searchView;
    private final LocalAudioPickerFragmentseekBarChangeListener seekBarChangeListener = new LocalAudioPickerFragmentseekBarChangeListener(this);
    private TextView totalTimeTv;

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(R.layout.fragment_audio_list_new, viewGroup, false);
    }

    
    public final class LocalAudioPickerFragmentseekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        final LocalAudioPickerFragment localAudioPickerFragment;

        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        LocalAudioPickerFragmentseekBarChangeListener(LocalAudioPickerFragment localAudioPickerFragment2) {
            this.localAudioPickerFragment = localAudioPickerFragment2;
        }

        public void onProgressChanged(SeekBar seekBar, int i, boolean z) {
            if (z) {
                this.localAudioPickerFragment.currentTimeTv.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer((long) i));
                this.localAudioPickerFragment.mediaPlayer.seekTo(i);
            }
        }
    }

    
    public final class LocalAudioPickerFragmentsearchOnTextChangeListener implements SearchView.OnQueryTextListener {
        final LocalAudioPickerFragment localAudioPickerFragment;

        LocalAudioPickerFragmentsearchOnTextChangeListener(LocalAudioPickerFragment localAudioPickerFragment2) {
            this.localAudioPickerFragment = localAudioPickerFragment2;
        }

        @Override 
        public boolean onQueryTextChange(String str) {
            this.localAudioPickerFragment.localAudioAdapter.getFilter().filter(str);
            return false;
        }

        @Override 
        public boolean onQueryTextSubmit(String str) {
            if (TextUtils.isEmpty(str)) {
                return false;
            }
            this.localAudioPickerFragment.localAudioAdapter.getFilter().filter(str);
            return false;
        }
    }

    
    public final class LocalAudioPickerFragmentRunnable implements Runnable {
        final LocalAudioPickerFragment localAudioPickerFragment;

        LocalAudioPickerFragmentRunnable(LocalAudioPickerFragment localAudioPickerFragment2) {
            this.localAudioPickerFragment = localAudioPickerFragment2;
        }

        public void run() {
            if (this.localAudioPickerFragment.mMediaHandler != null && this.localAudioPickerFragment.getActivity() != null && this.localAudioPickerFragment.mediaPlayer != null) {
                this.localAudioPickerFragment.getActivity().runOnUiThread(new Runnable() {
                    public final void run() {
                        try {
                            if (localAudioPickerFragment.mediaPlayer != null && localAudioPickerFragment.playerSeekbar != null && localAudioPickerFragment.currentTimeTv != null) {
                                int pos = localAudioPickerFragment.mediaPlayer.getCurrentPosition();
                                localAudioPickerFragment.playerSeekbar.setProgress(pos);
                                localAudioPickerFragment.currentTimeTv.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer((long) pos));
                            }
                        } catch (Exception ignored) {}
                    }
                });
                Handler handler = this.localAudioPickerFragment.mMediaHandler;
                if (handler != null) {
                    handler.postDelayed(this, 100);
                }
            }
        }
    }

    
    public static final class Companion {
        public Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public  LocalAudioPickerFragment newInstance() {
            return new LocalAudioPickerFragment();
        }
    }

    @Override 
    public void onViewCreated(View view, Bundle bundle) {
        initializeViews(view);
        this.mediaPlayer = new MediaPlayer();
        this.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        ArrayList<LocalAudio> audioList = getAudioList();
        if (audioList == null || audioList.size() <= 0) {
            this.recyclerView.setVisibility(View.GONE);
            this.noAudioTv.setVisibility(android.view.View.VISIBLE);
            return;
        }
        this.localAudioAdapter = new AudioAdapter(getContext(), audioList, new Function3<LocalAudio, Integer, View, Void>() {
            

            public Void apply(LocalAudio localAudio, Integer num, View view) throws Exception {
                LocalAudioPickerFragment.this.invoke(localAudio, num.intValue(), view);
                return null;
            }
        });
        this.recyclerView.setVisibility(android.view.View.VISIBLE);
        this.noAudioTv.setVisibility(View.GONE);
        this.recyclerView.setAdapter(this.localAudioAdapter);
        ImageView imageView = this.playPause;
        if (imageView != null) {
            imageView.setOnClickListener(new View.OnClickListener() {
                

                public  void onClick(View view) {
                    LocalAudioPickerFragment.loacalaudiofragmentlot(LocalAudioPickerFragment.this, view);
                }
            });
        }
        this.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            

            public  void onCompletion(MediaPlayer mediaPlayer) {
                LocalAudioPickerFragment.localaudiofr(LocalAudioPickerFragment.this, mediaPlayer);
            }
        });
    }

    public  void invoke(LocalAudio localAudio, int i, View view) {
        AudioAdapter audioAdapter;
        if (view.getId() == R.id.playLayout) {
            AudioAdapter audioAdapter2 = this.localAudioAdapter;
            Integer num = null;
            if ((audioAdapter2 != null ? Integer.valueOf(audioAdapter2.getCurrentPlayingPos()) : null).intValue() != i) {
                AudioAdapter audioAdapter3 = this.localAudioAdapter;
                if (audioAdapter3 != null) {
                    num = Integer.valueOf(audioAdapter3.getCurrentPlayingPos());
                }
                if (num.intValue() > -1 && (audioAdapter = this.localAudioAdapter) != null) {
                    audioAdapter.resetPreviouslyPlayedAudio();
                }
            }
            if (localAudio.isPlay()) {
                localAudio.setPlay(false);
            } else {
                localAudio.setPlay(true);
                localAudio.setHighlight(true);
                AudioAdapter audioAdapter4 = this.localAudioAdapter;
                if (audioAdapter4 != null) {
                    audioAdapter4.setCurrentPlayingPos(i);
                }
            }
            togglePlayer(i, localAudio);
        }
    }

    public static  void loacalaudiofragmentlot(LocalAudioPickerFragment localAudioPickerFragment, View view) {
        Object tag;
        AudioAdapter audioAdapter = localAudioPickerFragment.localAudioAdapter;
        Boolean bool = null;
        int intValue = (audioAdapter != null ? Integer.valueOf(audioAdapter.getCurrentPlayingPos()) : null).intValue();
        AudioAdapter audioAdapter2 = localAudioPickerFragment.localAudioAdapter;
        LocalAudio currentPlayingAudio = audioAdapter2 != null ? audioAdapter2.getCurrentPlayingAudio() : null;
        if (!(view == null || (tag = view.getTag()) == null)) {
            bool = Boolean.valueOf(tag.equals(true));
        }
        currentPlayingAudio.setPlay(!bool.booleanValue());
        localAudioPickerFragment.togglePlayer(intValue, currentPlayingAudio);
    }

    public static final void localaudiofr(LocalAudioPickerFragment localAudioPickerFragment, MediaPlayer mediaPlayer2) {
        MediaPlayer mediaPlayer3 = localAudioPickerFragment.mediaPlayer;
        Intrinsics.checkNotNull(mediaPlayer3);
        mediaPlayer3.pause();
        AudioAdapter audioAdapter = localAudioPickerFragment.localAudioAdapter;
        LocalAudio localAudio = null;
        Integer valueOf = audioAdapter != null ? Integer.valueOf(audioAdapter.getCurrentPlayingPos()) : null;
        Intrinsics.checkNotNull(valueOf);
        int intValue = valueOf.intValue();
        if (intValue > -1) {
            AudioAdapter audioAdapter2 = localAudioPickerFragment.localAudioAdapter;
            if (audioAdapter2 != null) {
                localAudio = audioAdapter2.getCurrentPlayingAudio();
            }
            Intrinsics.checkNotNull(localAudio);
            localAudio.setPlay(false);
            localAudioPickerFragment.togglePlayer(intValue, localAudio);
        }
    }

    private  void initializeViews(View view) {
        this.mediaPlayerLayout = (LinearLayout) view.findViewById(R.id.mediaPlayerLayout);
        this.currentTimeTv = (TextView) view.findViewById(R.id.current_time);
        this.totalTimeTv = (TextView) view.findViewById(R.id.total_time);
        this.recyclerView = (RecyclerView) view.findViewById(R.id.list);
        this.searchView = (SearchView) view.findViewById(R.id.search);
        this.playerSeekbar = (SeekBar) view.findViewById(R.id.player_seekbar);
        this.audioText = (TextView) view.findViewById(R.id.title);
        this.playPause = (ImageView) view.findViewById(R.id.play_pause);
        this.noAudioTv = (TextView) view.findViewById(R.id.no_audio);
        this.mMediaHandler = new Handler();
        SearchView searchView2 = this.searchView;
        if (searchView2 != null) {
            searchView2.setActivated(false);
        }
        SearchView searchView3 = this.searchView;
        if (searchView3 != null) {
            searchView3.setQueryHint("Type your filename here");
        }
        SearchView searchView4 = this.searchView;
        if (searchView4 != null) {
            searchView4.onActionViewExpanded();
        }
        SearchView searchView5 = this.searchView;
        if (searchView5 != null) {
            searchView5.setIconified(false);
        }
        SearchView searchView6 = this.searchView;
        if (searchView6 != null) {
            searchView6.clearFocus();
        }
        SearchView searchView7 = this.searchView;
        if (searchView7 != null) {
            searchView7.setOnQueryTextListener(this.searchOnTextChangeListener);
        }
    }

    public final void togglePlayer(int i, LocalAudio localAudio) {
        this.mediaPlayerLayout.setVisibility(android.view.View.VISIBLE);
        ImageView imageView = this.playPause;
        if (imageView != null) {
            imageView.setTag(Boolean.valueOf(localAudio.isPlay()));
        }
        AudioAdapter audioAdapter = this.localAudioAdapter;
        if (audioAdapter != null) {
            audioAdapter.notifyItemChanged(i, localAudio);
        }
        if (localAudio.isPlay()) {
            this.playPause.setImageResource(R.drawable.small_pause);
            if (this.mediaPlayer.isPlaying() || localAudio.getAudioId() != this.audioID) {
                this.totalTimeTv.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer(localAudio.getAudioDuration()));
                this.currentTimeTv.setText("00:00");
                this.playerSeekbar.setMax((int) localAudio.getAudioDuration());
                this.audioText.setText(localAudio.getAudioTitle());
                this.playerSeekbar.setOnSeekBarChangeListener(this.seekBarChangeListener);
                this.audioID = localAudio.getAudioId();
                this.mediaPlayer.stop();
                this.mediaPlayer.reset();
                try {
                    this.mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
                    this.mediaPlayer.setDataSource(requireActivity(), Uri.parse(localAudio.getAudioUri()));
                    this.mediaPlayer.prepare();
                    this.mediaPlayer.start();
                    startSeekbarUpdate();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                this.mediaPlayer.start();
                return;
            }
        } else {
            this.playPause.setImageResource(R.drawable.play);
            if (this.mediaPlayer.isPlaying()) {
                this.mediaPlayer.pause();
            }
        }
        RecyclerView recyclerView2 = this.recyclerView;
        if (recyclerView2 != null) {
            recyclerView2.scrollToPosition(i);
        }
    }

    private  void startSeekbarUpdate() {
        this.mMediaHandler.postDelayed(this.mMediaSeekRunnable, 100);
    }


    private  ArrayList<LocalAudio> getAudioList() {
        ArrayList<LocalAudio> arrayList = new ArrayList<>();

        
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED
        };

        
        Cursor query = requireContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Media.DATE_MODIFIED + " DESC"
        );

        if (query != null) {
            try {
                
                int columnIndexId = query.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int columnIndexTitle = query.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int columnIndexArtist = query.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int columnIndexDuration = query.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int columnIndexData = query.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int columnIndexDateModified = query.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);

                
                while (query.moveToNext()) {
                    try {
                        long id = query.getLong(columnIndexId);
                        String title = query.getString(columnIndexTitle);
                        String artist = query.getString(columnIndexArtist);
                        long duration = query.isNull(columnIndexDuration) ? 0 : query.getLong(columnIndexDuration);
                        String data = query.getString(columnIndexData);
                        String dateModified = query.getString(columnIndexDateModified);

                        
                        StringBuilder sb = new StringBuilder();
                        sb.append(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
                        sb.append(File.separator);
                        sb.append(getResources().getString(R.string.app_name));

                        
                        if (!data.contains(sb.toString())) {
                            arrayList.add(new LocalAudio(id, getAudioTitle(data, title), artist, duration, data, dateModified));
                        }
                    } catch (Exception e) {
                        
                        e.printStackTrace();
                    }
                }
            } finally {
                
                query.close();
            }
        }

        
        Collections.reverse(arrayList);

        return arrayList;
    }



    private  String getAudioTitle(String str, String str2) {
        List asList = Arrays.asList(str.split("/"));
        String str3 = (String) asList.get(asList.size() - 1);
        Arrays.asList(str3.split("."));
        if (!str.contains(str2 + '.' + ((String) asList.get(asList.size() - 1)))) {
            str3 = str2 + " - " + str3;
        }
        return str3.toString();
    }

    @Override 
    public void onDetach() {
        super.onDetach();
        if (this.mediaPlayer != null) {
            try {
                this.mediaPlayer.stop();
                this.mediaPlayer.release();
            } catch (Exception ignored) {}
            this.mediaPlayer = null;
        }
        if (this.mMediaHandler != null) {
            this.mMediaHandler.removeCallbacks(this.mMediaSeekRunnable);
        }
    }
}
