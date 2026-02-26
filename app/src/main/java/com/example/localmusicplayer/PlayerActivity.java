package com.example.localmusicplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class PlayerActivity extends AppCompatActivity {
    private ImageView albumArt;
    private TextView songTitle, songArtist, currentTime, duration;
    private ImageButton playPauseBtn, prevBtn, nextBtn, repeatBtn, shuffleBtn;
    private SeekBar seekBar;
    private Handler handler;
    private Runnable updateSeekBar;
    private MusicService musicService;
    private boolean serviceBound = false;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initializeViews();
        setupClickListeners();
        setupSeekBar();
        bindService();
    }

    private void initializeViews() {
        albumArt = findViewById(R.id.album_art_large);
        songTitle = findViewById(R.id.song_title_large);
        songArtist = findViewById(R.id.song_artist_large);
        playPauseBtn = findViewById(R.id.play_pause_btn);
        prevBtn = findViewById(R.id.prev_btn);
        nextBtn = findViewById(R.id.next_btn);
        repeatBtn = findViewById(R.id.repeat_btn);
        shuffleBtn = findViewById(R.id.shuffle_btn);
        seekBar = findViewById(R.id.seek_bar);
        currentTime = findViewById(R.id.current_time);
        duration = findViewById(R.id.duration);

        RecyclerView queueRecyclerView = findViewById(R.id.queue_recycler_view);
        queueRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        Toolbar toolbar = findViewById(R.id.player_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        handler = new Handler();
    }

    private void bindService() {
        Intent playIntent = new Intent(this, MusicService.class);
        bindService(playIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupClickListeners() {
        Toolbar toolbar = findViewById(R.id.player_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        playPauseBtn.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pausePlayer();
                    playPauseBtn.setImageResource(R.drawable.ic_play_arrow);
                } else {
                    musicService.go();
                    playPauseBtn.setImageResource(R.drawable.ic_pause);
                }
                // ИСПРАВЛЕНО: Обновляем уведомление при изменении состояния воспроизведения
                if (musicService.isPlaying()) {
                    musicService.updateNotification(musicService.createNotification());
                } else {
                    musicService.updateNotification(musicService.createNotification());
                }
                // ИСПРАВЛЕНО: Обновляем мини-плеер при изменении состояния воспроизведения
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    mainActivity.updateMiniPlayerUI();
                }
            }
        });

        prevBtn.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                musicService.playPrev();
                updateUI();
                // ИСПРАВЛЕНО: Обновляем мини-плеер при смене трека
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    mainActivity.updateMiniPlayerUI();
                }
                // ИСПРАВЛЕНО: Обновляем уведомление при смене трека
                musicService.updateNotification(musicService.createNotification());
            }
        });

        nextBtn.setOnClickListener(v -> {
            if (serviceBound && musicService != null) {
                musicService.playNext();
                updateUI();
                // ИСПРАВЛЕНО: Обновляем мини-плеер при смене трека
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    mainActivity.updateMiniPlayerUI();
                }
                // ИСПРАВЛЕНО: Обновляем уведомление при смене трека
                musicService.updateNotification(musicService.createNotification());
            }
        });

        repeatBtn.setOnClickListener(v -> {
            // Repeat mode toggle
        });

        shuffleBtn.setOnClickListener(v -> {
            // Shuffle mode toggle
            if (serviceBound && musicService != null) {
                musicService.setShuffle();
            }
        });
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (serviceBound && musicService != null) {
                        musicService.seek(progress);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (serviceBound && musicService != null) {
                    int currentPosition = musicService.getPosn();
                    int totalDuration = musicService.getDur();

                    seekBar.setMax(totalDuration);
                    seekBar.setProgress(currentPosition);

                    currentTime.setText(formatTime(currentPosition));
                    duration.setText(formatTime(totalDuration));
                }
                handler.postDelayed(this, 1000);
            }
        };
    }

    private void updateUI() {
        if (serviceBound && musicService != null) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                ArrayList<Song> songsList = mainActivity.getSongs();
                if (songsList != null && !songsList.isEmpty()) {
                    int currentPos = musicService.getCurrentSongPosition();
                    if (currentPos >= 0 && currentPos < songsList.size()) {
                        Song currentSong = songsList.get(currentPos);
                        songTitle.setText(currentSong.getTitle());
                        songArtist.setText(currentSong.getArtist());

                        // ИСПРАВЛЕНО: Загрузка изображения альбома
                        if (currentSong.getAlbumArt() != null && !currentSong.getAlbumArt().isEmpty()) {
                            Glide.with(this)
                                    .load(currentSong.getAlbumArt())
                                    .placeholder(R.drawable.ic_album)
                                    .into(albumArt);
                        } else {
                            albumArt.setImageResource(R.drawable.ic_album);
                        }

                        playPauseBtn.setImageResource(musicService.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

                        // ИСПРАВЛЕНО: Обновление состояния shuffle и repeat кнопок
                        shuffleBtn.setImageResource(musicService.isShuffle() ? R.drawable.ic_shuffle_on : R.drawable.ic_shuffle);
                    }
                }
            }
        }
    }

    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateSeekBar);
        updateUI(); // Обновляем UI при возврате в активность
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateSeekBar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
        }
    }
}