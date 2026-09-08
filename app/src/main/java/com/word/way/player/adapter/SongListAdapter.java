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
        int[] backgrounds = {
            R.drawable.card_tile_mint,
            R.drawable.card_tile_peach,
            R.drawable.card_tile_lavender,
            R.drawable.card_tile_yellow
        };
        if (holder.cardRoot != null) {
            holder.cardRoot.setBackgroundResource(backgrounds[Math.abs(position) % backgrounds.length]);
        }
        holder.name.setText((position + 1) + ". " + audio.getDisplayName());
        holder.duration.setText(audio.getDuration());
        holder.itemView.findViewById(R.id.play).setContentDescription("Play " + audio.getDisplayName());
        holder.itemView.setContentDescription(audio.getDisplayName() + ". Long press for sharing options.");
    }
    public final class AlbumViewHolder extends RecyclerView.ViewHolder {
        final View cardRoot;
        final TextView name, duration;
        AlbumViewHolder(View view) {
            super(view);
            cardRoot = view.findViewById(R.id.card_root);
            name = view.findViewById(R.id.title); duration = view.findViewById(R.id.duration);
            view.findViewById(R.id.play).setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= songListModelList.size()) return;
                SongListModel audio = songListModelList.get(position);
                context.startActivity(new Intent(context, MusicActivity.class).putExtra("SONG_URI", audio.getData())
                        .putExtra("SONG_INDEX", position).putExtra("SONG_NAME", audio.getDisplayName()));
            });
            view.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= songListModelList.size()) return false;
                String selectedPath = songListModelList.get(position).getData();
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.getMenu().add("Share recording");
                menu.setOnMenuItemClickListener(item -> {
                    RecordingShare.share(context, selectedPath);
                    return true;
                });
                menu.show(); return true;
            });
        }
    }
}
