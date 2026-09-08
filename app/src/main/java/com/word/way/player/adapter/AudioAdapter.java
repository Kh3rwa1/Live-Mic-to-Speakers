package com.word.way.player.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import com.word.way.R;
import com.word.way.Utils.ToolUi;
import com.word.way.player.AudioSearch;
import com.word.way.player.LocalAudio;
import com.word.way.player.MediaPlayerUtils;
import java.util.ArrayList;

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
    public AudioAdapter(Context context, ArrayList<LocalAudio> audio, AudioActionListener listener) {
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
        private final View cardRoot;
        private final TextView title, duration, modified, previewLabel;
        private final ImageView play;
        ViewHolder(View view) {
            super(view);
            cardRoot = view.findViewById(R.id.card_root);
            title = view.findViewById(R.id.title); duration = view.findViewById(R.id.duration);
            modified = view.findViewById(R.id.author); play = view.findViewById(R.id.iv_play);
            previewLabel = view.findViewById(R.id.library_preview_label);
            ToolUi.button(view.findViewById(R.id.playLayout));
            View.OnClickListener click = clicked -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= visible.size()) return;
                try { listener.onAudioAction(visible.get(position), position, clicked); }
                catch (RuntimeException error) {
                    Toast.makeText(context, R.string.library_action_error, Toast.LENGTH_LONG).show();
                }
            };
            view.findViewById(R.id.playLayout).setOnClickListener(click);
            view.findViewById(R.id.select_song).setOnClickListener(click);
        }
        void bind(LocalAudio audio) {
            cardRoot.setActivated(audio.isHighlight());
            title.setText(audio.getAudioTitle());
            duration.setText(context.getString(R.string.library_duration,
                    MediaPlayerUtils.INSTANCE.milliSecondsToTimer(Math.max(0, audio.getAudioDuration()))));
            try {
                long modifiedAt = Math.multiplyExact(Long.parseLong(audio.getDateModified()), 1000L);
                if (modifiedAt <= 0) throw new IllegalArgumentException("Unknown date");
                modified.setText(DateUtils.getRelativeTimeSpanString(modifiedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
                modified.setVisibility(View.VISIBLE);
            } catch (RuntimeException ignored) { modified.setText(null); modified.setVisibility(View.GONE); }
            play.setImageResource(audio.isPlay() ? R.drawable.tool_pause : R.drawable.tool_play);
            previewLabel.setText(audio.isPlay() ? R.string.library_pause : R.string.library_preview);
            itemView.findViewById(R.id.playLayout).setContentDescription(context.getString(
                    audio.isPlay() ? R.string.library_pause_named : R.string.library_preview_named, audio.getAudioTitle()));
            itemView.findViewById(R.id.select_song).setContentDescription(context.getString(R.string.library_open_named, audio.getAudioTitle()));
            title.setSelected(audio.isHighlight());
        }
    }
}
