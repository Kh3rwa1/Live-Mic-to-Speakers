package com.word.way.player.adapter;

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
import com.word.way.R;
import com.word.way.player.AudioSearch;
import com.word.way.player.LocalAudio;
import com.word.way.player.MediaPlayerUtils;
import java.util.ArrayList;
import java.util.List;

/** Playback identity survives filtering. Clicks resolve their current binding position. */
public final class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> implements Filterable {
    public interface AudioActionListener {
        void onAudioAction(LocalAudio audio, int position, View clicked);
    }
    private final Context context;
    private final AudioActionListener listener;
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
            AudioActionListener listener) {
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
    @Override public void onBindViewHolder(ViewHolder holder, int position) { holder.bind(visible.get(position), position); }
    public final class ViewHolder extends RecyclerView.ViewHolder {
        private final View cardRoot;
        private final TextView title, duration, modified, location;
        private final ImageView play;
        ViewHolder(View view) {
            super(view);
            cardRoot = view.findViewById(R.id.card_root);
            title = view.findViewById(R.id.title); duration = view.findViewById(R.id.duration);
            modified = view.findViewById(R.id.author); location = view.findViewById(R.id.location); play = view.findViewById(R.id.iv_play);
            View.OnClickListener click = clicked -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= visible.size()) return;
                try { listener.onAudioAction(visible.get(position), position, clicked); }
                catch (RuntimeException error) { android.util.Log.w("AudioAdapter", "Audio selection failed", error); }
            };
            view.findViewById(R.id.playLayout).setOnClickListener(click);
            view.findViewById(R.id.select_song).setOnClickListener(click);
        }
        void bind(LocalAudio audio, int position) {
            int[] backgrounds = {
                R.drawable.card_tile_mint,
                R.drawable.card_tile_peach,
                R.drawable.card_tile_lavender,
                R.drawable.card_tile_yellow
            };
            if (cardRoot != null) {
                cardRoot.setBackgroundResource(backgrounds[Math.abs(position) % backgrounds.length]);
            }
            title.setText((position + 1) + ". " + audio.getAudioTitle());
            duration.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer(Math.max(0, audio.getAudioDuration())));
            try { modified.setText(MediaPlayerUtils.INSTANCE.getTimeAgo(audio.getDateModified(), context)); }
            catch (RuntimeException ignored) { modified.setText(""); }
            Uri uri = audio.getAudioUri() == null ? Uri.EMPTY : Uri.parse(audio.getAudioUri());
            List<String> segments = uri.getPathSegments();
            location.setText("content".equals(uri.getScheme()) ? "Audio library"
                    : segments.size() > 1 ? segments.get(segments.size() - 2) : "Audio");
            play.setImageResource(audio.isPlay() ? R.drawable.tool_pause : R.drawable.tool_play);
            itemView.findViewById(R.id.playLayout).setContentDescription((audio.isPlay() ? "Pause " : "Preview ") + audio.getAudioTitle());
            itemView.findViewById(R.id.select_song).setContentDescription("Open " + audio.getAudioTitle());
            title.setSelected(audio.isHighlight());
        }
    }
}
