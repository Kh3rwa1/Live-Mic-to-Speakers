package com.example.livemictospeaker.player.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.activity.MusicActivity;
import com.example.livemictospeaker.player.SongListModel;
import java.util.ArrayList;
import java.util.List;


public class SongListAdapter extends RecyclerView.Adapter<SongListAdapter.AlbumViewHolder> {
    public Context context;
    public List<SongListModel> songListModelList;

    
    public class AlbumViewHolder extends RecyclerView.ViewHolder {
        LinearLayout play;
        TextView tvSongDuration;
        TextView tvSongName;

        AlbumViewHolder(View view) {
            super(view);
            this.tvSongName = (TextView) view.findViewById(R.id.title);
            this.tvSongDuration = (TextView) view.findViewById(R.id.duration);
            this.play = (LinearLayout) view.findViewById(R.id.play);
        }
    }

    public SongListAdapter(Context context2, List<SongListModel> list) {
        this.songListModelList = list;
        this.context = context2;
    }

    public void filterList(ArrayList<SongListModel> arrayList) {
        this.songListModelList = arrayList;
        notifyDataSetChanged();
    }

    @Override 
    public AlbumViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new AlbumViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_audio_layout_new, viewGroup, false));
    }

    public void onBindViewHolder(AlbumViewHolder albumViewHolder, @SuppressLint("RecyclerView") final int i) {
        albumViewHolder.tvSongName.setText(this.songListModelList.get(i).getDisplayName());
        TextView textView = albumViewHolder.tvSongDuration;
        textView.setText(this.songListModelList.get(i).getDuration() + "");
        albumViewHolder.play.setOnClickListener(new View.OnClickListener() {
            

            public void onClick(View view) {
                Intent intent = new Intent(SongListAdapter.this.context, MusicActivity.class);
                intent.putExtra("SONG_URI", SongListAdapter.this.songListModelList.get(i).getData());
                intent.putExtra("SONG_INDEX", i);
                intent.putExtra("SONG_NAME", SongListAdapter.this.songListModelList.get(i).getDisplayName());
                SongListAdapter.this.context.startActivity(intent);
            }
        });
    }

    @Override 
    public int getItemCount() {
        return this.songListModelList.size();
    }
}
