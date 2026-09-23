package com.word.way.player.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.word.way.R;
import com.word.way.util.ToolUi;
import com.word.way.player.AudioSearch;
import com.word.way.player.LocalAudio;
import com.word.way.player.MediaPlayerUtils;
import com.word.way.view.AudioWaveformBarView;
import java.util.ArrayList;
import java.util.Locale;

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
    public ArrayList<LocalAudio> getVisibleList() { return visible; }
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
        private final TextView title, duration, modified, previewLabel, format, waveformDuration;
        private final ImageView play, musicIcon;
        private final FrameLayout badgeBg;
        private final ImageButton moreOptions, favorite;
        private final AudioWaveformBarView waveformBar;
        private final Button selectSong;
        ViewHolder(View view) {
            super(view);
            cardRoot = view.findViewById(R.id.card_root);
            title = view.findViewById(R.id.title); duration = view.findViewById(R.id.duration);
            modified = view.findViewById(R.id.author); play = view.findViewById(R.id.iv_play);
            previewLabel = view.findViewById(R.id.library_preview_label);
            format = view.findViewById(R.id.tv_format);
            waveformDuration = view.findViewById(R.id.tv_waveform_duration);
            musicIcon = view.findViewById(R.id.iv_music_icon);
            badgeBg = view.findViewById(R.id.iv_music_badge_bg);
            moreOptions = view.findViewById(R.id.iv_more_options);
            favorite = view.findViewById(R.id.iv_favorite);
            waveformBar = view.findViewById(R.id.waveform_bar);
            selectSong = view.findViewById(R.id.select_song);
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
            String durationFormatted = MediaPlayerUtils.INSTANCE.milliSecondsToTimer(Math.max(0, audio.getAudioDuration()));
            duration.setText(durationFormatted);
            if (waveformDuration != null) waveformDuration.setText(durationFormatted);
            try {
                long modifiedAt = Math.multiplyExact(Long.parseLong(audio.getDateModified()), 1000L);
                if (modifiedAt <= 0) throw new IllegalArgumentException("Unknown date");
                modified.setText(DateUtils.getRelativeTimeSpanString(modifiedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
                modified.setVisibility(View.VISIBLE);
            } catch (RuntimeException ignored) { modified.setText(null); modified.setVisibility(View.GONE); }

            if (format != null) {
                String name = audio.getAudioTitle();
                String ext = "MP3";
                if (name != null && name.contains(".")) {
                    ext = name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
                    if (ext.length() > 4) ext = "MP3";
                }
                format.setText(context.getString(R.string.audio_format_display, ext));
            }

            int themeIndex = Math.abs(audio.getAudioTitle() == null ? 0 : audio.getAudioTitle().hashCode()) % 3;
            if (badgeBg != null) {
                if (themeIndex == 1) {
                    badgeBg.setBackgroundResource(R.drawable.bg_squircle_lavender_soft);
                    if (musicIcon != null) musicIcon.setColorFilter(
                            androidx.core.content.ContextCompat.getColor(context, R.color.badge_icon_purple));
                    if (selectSong != null) selectSong.setBackgroundResource(R.drawable.btn_pill_open_player_purple);
                } else if (themeIndex == 2) {
                    badgeBg.setBackgroundResource(R.drawable.bg_squircle_mint_soft);
                    if (musicIcon != null) musicIcon.setColorFilter(
                            androidx.core.content.ContextCompat.getColor(context, R.color.badge_icon_teal));
                    if (selectSong != null) selectSong.setBackgroundResource(R.drawable.btn_pill_open_player_mint);
                } else {
                    badgeBg.setBackgroundResource(R.drawable.bg_squircle_peach_soft);
                    if (musicIcon != null) musicIcon.setColorFilter(
                            androidx.core.content.ContextCompat.getColor(context, R.color.badge_icon_coral));
                    if (selectSong != null) selectSong.setBackgroundResource(R.drawable.btn_pill_open_player_peach);
                }
            }

            if (waveformBar != null) {
                waveformBar.setTheme(themeIndex);
                waveformBar.setWaveformSeed(audio.getAudioId());
                waveformBar.setProgress(audio.isPlay() ? 0.65f : 0.35f);
            }

            if (favorite != null) {
                favorite.setImageResource(audio.isHighlight() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                favorite.setOnClickListener(v -> {
                    audio.setHighlight(!audio.isHighlight());
                    favorite.setImageResource(audio.isHighlight() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                });
            }

            if (moreOptions != null) {
                moreOptions.setOnClickListener(v -> {
                    PopupMenu menu = new PopupMenu(context, moreOptions);
                    menu.getMenu().add(context.getString(R.string.library_share));
                    menu.setOnMenuItemClickListener(item -> {
                        try {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("audio/*");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(audio.getAudioUri()));
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.library_share_named, audio.getAudioTitle())));
                        } catch (Exception ignored) { }
                        return true;
                    });
                    menu.show();
                });
            }

            play.setImageResource(audio.isPlay() ? R.drawable.tool_pause : R.drawable.tool_play);
            previewLabel.setText(audio.isPlay() ? R.string.library_pause : R.string.library_preview);
            itemView.findViewById(R.id.playLayout).setContentDescription(context.getString(
                    audio.isPlay() ? R.string.library_pause_named : R.string.library_preview_named, audio.getAudioTitle()));
            itemView.findViewById(R.id.select_song).setContentDescription(context.getString(R.string.library_open_named, audio.getAudioTitle()));
            title.setSelected(audio.isHighlight());
        }
    }
}
