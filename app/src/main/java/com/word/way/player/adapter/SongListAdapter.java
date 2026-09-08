package com.word.way.player.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.word.way.R;
import com.word.way.activity.MusicActivity;
import com.word.way.player.RecordingShare;
import com.word.way.player.SongListModel;
import java.util.ArrayList;
import java.util.List;

/** Named, visible play/share actions; long-press sharing remains available for existing users. */
public class SongListAdapter extends RecyclerView.Adapter<SongListAdapter.AlbumViewHolder> {
    public final Context context;
    public List<SongListModel> songListModelList;
    public SongListAdapter(Context context, List<SongListModel> list) { this.context = context; songListModelList = new ArrayList<>(list); }
    public void filterList(ArrayList<SongListModel> list) { songListModelList = new ArrayList<>(list); notifyDataSetChanged(); }
    @Override public int getItemCount() { return songListModelList.size(); }
    @Override public AlbumViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        return new AlbumViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audio_layout_new, parent, false));
    }
    @Override public void onBindViewHolder(AlbumViewHolder holder, int position) {
        SongListModel audio = songListModelList.get(position);
        holder.name.setText(audio.getDisplayName());
        holder.duration.setText(context.getString(R.string.library_duration, audio.getDuration()));
        holder.itemView.findViewById(R.id.play).setContentDescription(context.getString(R.string.library_play_named, audio.getDisplayName()));
        holder.itemView.findViewById(R.id.share_recording).setContentDescription(context.getString(R.string.library_share_named, audio.getDisplayName()));
    }
    public final class AlbumViewHolder extends RecyclerView.ViewHolder {
        final TextView name, duration;
        AlbumViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.title); duration = view.findViewById(R.id.duration);
            view.findViewById(R.id.play).setOnClickListener(v -> {
                SongListModel audio = current();
                if (audio == null) return;
                context.startActivity(new Intent(context, MusicActivity.class).putExtra("SONG_URI", audio.getData())
                        .putExtra("SONG_INDEX", getBindingAdapterPosition()).putExtra("SONG_NAME", audio.getDisplayName()));
            });
            view.findViewById(R.id.share_recording).setOnClickListener(v -> {
                SongListModel audio = current();
                if (audio != null) RecordingShare.share(context, audio.getData());
            });
            view.setOnLongClickListener(v -> {
                SongListModel audio = current();
                if (audio == null) return false;
                String selectedPath = audio.getData();
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.getMenu().add(R.string.library_share_recording);
                menu.setOnMenuItemClickListener(item -> { RecordingShare.share(context, selectedPath); return true; });
                menu.show(); return true;
            });
        }
        private SongListModel current() {
            int position = getBindingAdapterPosition();
            return position == RecyclerView.NO_POSITION || position >= songListModelList.size() ? null : songListModelList.get(position);
        }
    }
}
