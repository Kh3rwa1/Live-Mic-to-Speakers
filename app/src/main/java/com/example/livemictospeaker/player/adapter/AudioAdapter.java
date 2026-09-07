package com.example.livemictospeaker.player.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.player.AudioSearch;
import com.example.livemictospeaker.player.LocalAudio;
import com.example.livemictospeaker.player.MediaPlayerUtils;
import java.util.ArrayList;
import java.util.List;
import io.reactivex.functions.Function3;

/** Playback identity survives filtering. Clicks resolve their current binding position. */
public final class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> implements Filterable {
    private final Context context;
    private final Function3<? super LocalAudio, ? super Integer, ? super View, Void> listener;
    public final ArrayList<LocalAudio> localAudioLists;
    private ArrayList<LocalAudio> visible;
    private LocalAudio current;
    private final Filter filter = new Filter() {
        @Override protected FilterResults performFiltering(CharSequence query) {
            ArrayList<LocalAudio> found = new ArrayList<>();
            for (LocalAudio audio : localAudioLists) if (AudioSearch.matches(audio.getAudioTitle(), query)) found.add(audio);
            FilterResults result = new FilterResults(); result.values = found; result.count = found.size(); return result;
        }
        @Override @SuppressWarnings("unchecked") protected void publishResults(CharSequence query, FilterResults results) {
            visible = results.values instanceof ArrayList ? (ArrayList<LocalAudio>) results.values : new ArrayList<>();
            notifyDataSetChanged();
        }
    };
    public AudioAdapter(Context context, ArrayList<LocalAudio> audio,
            Function3<? super LocalAudio, ? super Integer, ? super View, Void> listener) {
        this.context = context; this.listener = listener;
        localAudioLists = new ArrayList<>(audio); visible = new ArrayList<>(audio);
    }
    public Context getContext() { return context; }
    public void setMLocalAudioList(ArrayList<LocalAudio> list) { visible = new ArrayList<>(list); }
    @Override public Filter getFilter() { return filter; }
    public void setCurrentPlayingPos(int position) { current = position >= 0 && position < visible.size() ? visible.get(position) : null; }
    public int getCurrentPlayingPos() { return visible.indexOf(current); }
    public LocalAudio getCurrentPlayingAudio() { return current; }
    public void resetPreviouslyPlayedAudio() {
        if (current != null) { current.setPlay(false); current.setHighlight(false); }
        notifyDataSetChanged();
    }
    @Override public int getItemCount() { return visible.size(); }
    @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.music_audio_layout_new, parent, false));
    }
    @Override public void onBindViewHolder(ViewHolder holder, int position) { holder.bind(visible.get(position)); }
    public final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title, duration, modified, location;
        private final ImageView play;
        ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.title); duration = view.findViewById(R.id.duration);
            modified = view.findViewById(R.id.author); location = view.findViewById(R.id.location); play = view.findViewById(R.id.iv_play);
            View.OnClickListener click = clicked -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= visible.size()) return;
                try { listener.apply(visible.get(position), position, clicked); }
                catch (Exception error) { android.util.Log.w("AudioAdapter", "Audio selection failed", error); }
            };
            view.findViewById(R.id.playLayout).setOnClickListener(click);
            view.findViewById(R.id.select_song).setOnClickListener(click);
        }
        void bind(LocalAudio audio) {
            title.setText(audio.getAudioTitle());
            duration.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer(Math.max(0, audio.getAudioDuration())));
            try { modified.setText(MediaPlayerUtils.INSTANCE.getTimeAgo(audio.getDateModified(), context)); }
            catch (RuntimeException ignored) { modified.setText(""); }
            Uri uri = audio.getAudioUri() == null ? Uri.EMPTY : Uri.parse(audio.getAudioUri());
            List<String> segments = uri.getPathSegments();
            location.setText("content".equals(uri.getScheme()) ? "Audio library"
                    : segments.size() > 1 ? segments.get(segments.size() - 2) : "Audio");
            play.setImageResource(audio.isPlay() ? R.drawable.small_pause : R.drawable.small_play);
            itemView.findViewById(R.id.playLayout).setContentDescription((audio.isPlay() ? "Pause " : "Preview ") + audio.getAudioTitle());
            itemView.findViewById(R.id.select_song).setContentDescription("Open " + audio.getAudioTitle());
            title.setSelected(audio.isHighlight());
        }
    }
}
