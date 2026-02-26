package com.example.localmusicplayer;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {
    private ArrayList<Song> songs;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Song song, int position);
    }

    public SongAdapter(ArrayList<Song> songs) {
        this.songs = songs;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.titleTextView.setText(song.getTitle());
        holder.artistTextView.setText(song.getArtist());
        holder.durationTextView.setText(song.getDuration());

        // Загрузка обложки альбома
        if (song.getAlbumArt() != null && !song.getAlbumArt().isEmpty()) {
            Glide.with(holder.imageView.getContext())
                    .load(song.getAlbumArt())
                    .placeholder(R.drawable.ic_album)
                    .into(holder.imageView);
        } else {
            holder.imageView.setImageResource(R.drawable.ic_album);
        }
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleTextView;
        TextView artistTextView;
        TextView durationTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.album_art);
            titleTextView = itemView.findViewById(R.id.song_title);
            artistTextView = itemView.findViewById(R.id.song_artist);
            durationTextView = itemView.findViewById(R.id.song_duration);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    Log.d("ClickPOS", String.valueOf(position));
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemClick(songs.get(position), position);
                    }
                }
            });
        }
    }
}