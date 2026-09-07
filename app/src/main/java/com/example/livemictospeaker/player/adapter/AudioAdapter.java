package com.example.livemictospeaker.player.adapter;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.player.LocalAudio;
import com.example.livemictospeaker.player.MediaPlayerUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import kotlin.jvm.internal.Intrinsics;
import io.reactivex.functions.Function3;


public final class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> implements Filterable {
    private final Context context;
    private final Function3<LocalAudio, Integer, View, Void> listener;
    public final ArrayList<LocalAudio> localAudioLists;
    private int mCurrentPlayingPos = -1;
    private ArrayList<LocalAudio> mLocalAudioList;
    private ValueFilter valueFilter;

    
    public final class ValueFilter extends Filter {
        public ValueFilter() {
        }

        public void publishResults(CharSequence charSequence, FilterResults filterResults) {
            AudioAdapter.this.setMLocalAudioList((ArrayList) filterResults.values);
            AudioAdapter.this.notifyDataSetChanged();
        }

        public FilterResults performFiltering(CharSequence charSequence) {
            Boolean bool;
            FilterResults filterResults = new FilterResults();
            if (TextUtils.isEmpty(charSequence)) {
                filterResults.count = AudioAdapter.this.localAudioLists.size();
                filterResults.values = AudioAdapter.this.localAudioLists;
            } else {
                ArrayList arrayList = new ArrayList();
                Iterator<LocalAudio> it = AudioAdapter.this.localAudioLists.iterator();
                while (it.hasNext()) {
                    LocalAudio next = it.next();
                    String audioTitle = next.getAudioTitle();
                    if (audioTitle != null) {
                        Intrinsics.checkNotNull(charSequence);
                        bool = Boolean.valueOf(audioTitle.contains(audioTitle));
                    } else {
                        bool = null;
                    }
                    Intrinsics.checkNotNull(bool);
                    if (bool.booleanValue()) {
                        arrayList.add(next);
                    }
                }
                filterResults.count = arrayList.size();
                filterResults.values = arrayList;
            }
            return filterResults;
        }
    }

    
    public static final class ViewHolder extends RecyclerView.ViewHolder {
        private LinearLayout addLayout;
        private TextView dateModified;
        private TextView duration;
        private TextView location;
        private final Context mContext;
        private ImageView play;
        private LinearLayout playLayout;
        private TextView title;

        public ViewHolder(View view, Context context) {
            super(view);
            this.title = (TextView) view.findViewById(R.id.title);
            this.duration = (TextView) view.findViewById(R.id.duration);
            this.dateModified = (TextView) view.findViewById(R.id.author);
            this.location = (TextView) view.findViewById(R.id.location);
            this.addLayout = (LinearLayout) view.findViewById(R.id.select_song);
            this.playLayout = (LinearLayout) view.findViewById(R.id.playLayout);
            this.play = (ImageView) view.findViewById(R.id.iv_play);
            this.mContext = context;
        }

        public  TextView getTitle() {
            return this.title;
        }

        public  void setTitle(TextView textView) {
            this.title = textView;
        }

        public  TextView getDuration() {
            return this.duration;
        }

        public  void setDuration(TextView textView) {
            this.duration = textView;
        }

        public  ImageView getPlay() {
            return this.play;
        }

        public  void setPlay(ImageView imageView) {
            this.play = imageView;
        }

        public  void togglePlayButton(boolean z, boolean z2) {
            int i;
            this.play.setImageResource(z ? R.drawable.small_play : R.drawable.small_pause);
            TextView textView = this.title;
            if (z2) {
                i = Color.parseColor("#1F92C6");
            } else {
                i = ContextCompat.getColor(this.mContext, R.color.white);
            }
            textView.setTextColor(i);
        }

        public final void bind(final int i, final LocalAudio localAudio, final Function3<? super LocalAudio, ? super Integer, ? super View, Void> function3) {
            View view = this.itemView;
            String milliSecondsToTimer = MediaPlayerUtils.INSTANCE.milliSecondsToTimer(localAudio.getAudioDuration());
            String audioUri = localAudio.getAudioUri();
            List asList = audioUri != null ? Arrays.asList(audioUri.split("/")) : null;
            StringBuilder sb = new StringBuilder();
            Intrinsics.checkNotNull(asList);
            sb.append((String) asList.get(asList.size() - 3));
            sb.append('/');
            sb.append((String) asList.get(asList.size() - 2));
            String sb2 = sb.toString();
            this.title.setText(localAudio.getAudioTitle());
            TextView textView = this.dateModified;
            MediaPlayerUtils mediaPlayerUtils = MediaPlayerUtils.INSTANCE;
            String dateModified2 = localAudio.getDateModified();
            Intrinsics.checkNotNull(dateModified2);
            Context context = view.getContext();
            Intrinsics.checkNotNullExpressionValue(context, "context");
            textView.setText(mediaPlayerUtils.getTimeAgo(dateModified2, context));
            this.location.setText(sb2);
            this.duration.setText(milliSecondsToTimer);
            this.playLayout.setTag(localAudio);
            this.playLayout.setOnClickListener(new View.OnClickListener() {
                

                public  void onClick(View view) {
                    try {
                        ViewHolder.playof(function3, localAudio, i, view);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            this.addLayout.setOnClickListener(new View.OnClickListener() {
                

                public  void onClick(View view) {
                    try {
                        ViewHolder.addlayoutof(function3, localAudio, i, view);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            togglePlayButton(!localAudio.isPlay(), localAudio.isHighlight());
        }

        public static  void playof(Function3 function3, LocalAudio localAudio, int i, View view) throws Exception {
            function3.apply(localAudio, Integer.valueOf(i), view);
        }

        public static  void addlayoutof(Function3 function3, LocalAudio localAudio, int i, View view) throws Exception {
            function3.apply(localAudio, Integer.valueOf(i), view);
        }
    }

    public  Context getContext() {
        return this.context;
    }

    public  Function3<LocalAudio, Integer, View, Void> getListener() {
        return this.listener;
    }

    
    
    public AudioAdapter(Context context2, ArrayList<LocalAudio> arrayList, Function3<? super LocalAudio, ? super Integer, ? super View, Void> function3) {
        this.context = context2;
        this.localAudioLists = arrayList;
        this.listener = (Function3<LocalAudio, Integer, View, Void>) function3;
        this.mLocalAudioList = (ArrayList) arrayList.clone();
    }

    public  void setMLocalAudioList(ArrayList<LocalAudio> arrayList) {
        this.mLocalAudioList = arrayList;
    }

    @Override 
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.music_audio_layout_new, viewGroup, false), this.context);
    }

    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        viewHolder.bind(i, this.mLocalAudioList.get(i), this.listener);
    }

    @Override 
    public int getItemCount() {
        return this.mLocalAudioList.size();
    }

    public Filter getFilter() {
        if (this.valueFilter == null) {
            this.valueFilter = new ValueFilter();
        }
        ValueFilter valueFilter2 = this.valueFilter;
        notifyDataSetChanged();
        return valueFilter2;
    }

    public  void setCurrentPlayingPos(int i) {
        this.mCurrentPlayingPos = i;
    }

    public  int getCurrentPlayingPos() {
        return this.mCurrentPlayingPos;
    }

    public  LocalAudio getCurrentPlayingAudio() {
        return this.mLocalAudioList.get(this.mCurrentPlayingPos);
    }

    public  void resetPreviouslyPlayedAudio() {
        LocalAudio localAudio = this.mLocalAudioList.get(this.mCurrentPlayingPos);
        localAudio.setPlay(false);
        localAudio.setHighlight(false);
        notifyItemChanged(this.mCurrentPlayingPos, localAudio);
    }
}
