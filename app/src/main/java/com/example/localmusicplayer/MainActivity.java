package com.example.localmusicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ArrayList<Song> songs;
    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private MusicService musicService;
    private boolean serviceBound = false;
    private Handler handler;
    private SeekBar seekBar;
    private TextView currentTime, duration;
    private ImageButton playPauseBtn, prevBtn, nextBtn;
    private FrameLayout miniPlayerContainer;
    private ImageView miniAlbumArt, miniPlayPauseBtn;
    private TextView miniSongTitle, miniSongArtist;
    private TextInputEditText SearchField;
    private String SearchSongValue;
    public static MainActivity instance;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            musicService.setList(songs);
            serviceBound = true;
            Log.d("MainActivity", "Service connected successfully");

            // Обновляем UI при подключении к сервису
            updateMiniPlayerUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            musicService = null;
            Log.d("MainActivity", "Service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instance = this; // Установка статического экземпляра
        initializeViews();
        requestPermissions();
        loadSongs();
        updateSeeks();
    }


    private void updateSeeks() {
        Handler handler = new Handler(Looper.getMainLooper());

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (musicService == null) {return;}
                float maxPos = musicService.getDur();
                float pos = musicService.getPosn();
                seekBar.setMax(100);
                seekBar.setProgress((int) (pos/maxPos)*100);

                handler.postDelayed(this, 1000); // повтор через 1 секунду
            }
        };
        handler.post(runnable);

    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.songs_recycler_view);
        miniPlayerContainer = findViewById(R.id.mini_player_container);
        miniAlbumArt = findViewById(R.id.mini_album_art);
        miniSongTitle = findViewById(R.id.mini_song_title);
        miniSongArtist = findViewById(R.id.mini_song_artist);
        SearchField = findViewById(R.id.search_input);

        // Initialize controls
        playPauseBtn = findViewById(R.id.play_pause_btn);
        prevBtn = findViewById(R.id.prev_btn);
        nextBtn = findViewById(R.id.next_btn);
        seekBar = findViewById(R.id.seek_bar);
        currentTime = findViewById(R.id.current_time);
        duration = findViewById(R.id.duration);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        songs = new ArrayList<>();
        adapter = new SongAdapter(songs);
        recyclerView.setAdapter(adapter);

        SearchField.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        SearchSongValue = charSequence.toString().toLowerCase();
                        loadSongs();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {

                    }
                }
        );

        // ИСПРАВЛЕНО: Проверяем serviceBound перед использованием musicService
        adapter.setOnItemClickListener((song, position) -> {
            // Запускаем и подключаемся к сервису, если еще не подключены
            if (!serviceBound) {
                startAndBindService();
                // Показываем сообщение, что сервис еще не готов
                Toast.makeText(this, "Service is starting, please wait...", Toast.LENGTH_SHORT).show();
                return; // Выходим из метода, не продолжаем выполнение
            }
            // Если сервис подключен, продолжаем
            if (musicService != null) { // ИСПРАВЛЕНО: Добавлена проверка на null
                musicService.setSong(position);
                musicService.playSong();
                showMiniPlayer();
                updateMiniPlayerUI();

                // Start PlayerActivity when clicking on a song
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                startActivity(intent);
            }
        });


        handler = new Handler();
    }

    public void showPlayer(View view) {
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        startActivity(intent);
    }

    public ArrayList<Song> getSongs() {
        return songs;
    }

    private void requestPermissions() {
        ActivityResultLauncher<String[]> permissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        loadSongs();
                    } else {
                        Toast.makeText(this, "Permissions required to access music files", Toast.LENGTH_LONG).show();
                    }
                });

        String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_AUDIO};
        }
        permissionLauncher.launch(permissions);
    }

    private void loadSongs() {
        new LoadSongsTask().execute();
    }

    private class LoadSongsTask extends AsyncTask<Void, Void, ArrayList<Song>> {
        @Override
        protected ArrayList<Song> doInBackground(Void... voids) {
            ArrayList<Song> songList = new ArrayList<>();
            ContentResolver contentResolver = getContentResolver();
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
            Cursor cursor = contentResolver.query(uri, null, null, null, sortOrder);

            if (cursor != null && cursor.moveToFirst()) {
                int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);

                do {
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long duration = cursor.getLong(durationColumn);
                    String data = cursor.getString(dataColumn);
                    long albumId = cursor.getLong(albumIdColumn);
                    long id = cursor.getLong(idColumn);

                    String durationFormatted = formatTime(duration);

                    // Get album art URI
                    String albumArt = getAlbumArtUri(albumId);

                    if (title != null && !title.isEmpty()) {
                        if (SearchSongValue != null && !SearchSongValue.isEmpty()) {
                            if (!title.toLowerCase().contains(SearchSongValue) && !artist.toLowerCase().contains(SearchSongValue)) {
                                continue;
                            }
                        }

                        Song song = new Song(title, artist, album, durationFormatted, data, albumArt, id);
                        song.setDurationInMs((int) duration);
                        songList.add(song);
                    }
                } while (cursor.moveToNext());

                cursor.close();
            }

            return songList;
        }

        @Override
        protected void onPostExecute(ArrayList<Song> songList) {
            songs.clear();
            songs.addAll(songList);
            adapter.notifyDataSetChanged();
        }
    }

    private String getAlbumArtUri(long albumId) {
        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = android.content.ContentUris.withAppendedId(sArtworkUri, albumId);
        return uri.toString();
    }

    private String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void startAndBindService() {
        Intent playIntent = new Intent(this, MusicService.class);
        startService(playIntent); // Сначала запускаем сервис
        bindService(playIntent, serviceConnection, Context.BIND_AUTO_CREATE); // Затем подключаемся
    }

    private void showMiniPlayer() {
        miniPlayerContainer.setVisibility(View.VISIBLE);
    }

    // ИСПРАВЛЕНО: Улучшена логика обновления мини-плеера
    public void updateMiniPlayerUI() {
        if (musicService != null && songs.size() > 0) {
            int currentPos = musicService.getCurrentSongPosition();
            if (currentPos >= 0 && currentPos < songs.size()) {
                Song currentSong = songs.get(currentPos);

                // Обновляем название и исполнителя
                miniSongTitle.setText(currentSong.getTitle());
                miniSongArtist.setText(currentSong.getArtist());

                // Обновляем изображение альбома
                if (currentSong.getAlbumArt() != null && !currentSong.getAlbumArt().isEmpty()) {
                    Glide.with(this)
                            .load(currentSong.getAlbumArt())
                            .placeholder(R.drawable.ic_album)
                            .into(miniAlbumArt);
                } else {
                    miniAlbumArt.setImageResource(R.drawable.ic_album);
                }
            }
        }
    }

    public void playPause() {
        if (serviceBound && musicService != null) {
            if (musicService.isPlaying()) {
                musicService.pausePlayer();
                miniPlayPauseBtn.setImageResource(R.drawable.ic_play_arrow);
            } else {
                musicService.go();
                miniPlayPauseBtn.setImageResource(R.drawable.ic_pause);
            }
            // ИСПРАВЛЕНО: Обновляем уведомление при изменении состояния воспроизведения
            if (musicService.isPlaying()) {
                musicService.updateNotification(musicService.createNotification());
            } else {
                musicService.updateNotification(musicService.createNotification());
            }
        }
    }

    public void playNext() {
        if (serviceBound && musicService != null) {
            musicService.playNext();
            // ИСПРАВЛЕНО: Обновляем мини-плеер при смене трека
            updateMiniPlayerUI();
            // ИСПРАВЛЕНО: Обновляем уведомление при смене трека
            musicService.updateNotification(musicService.createNotification());
        }
    }

    public void playPrev() {
        if (serviceBound && musicService != null) {
            musicService.playPrev();
            // ИСПРАВЛЕНО: Обновляем мини-плеер при смене трека
            updateMiniPlayerUI();
            // ИСПРАВЛЕНО: Обновляем уведомление при смене трека
            musicService.updateNotification(musicService.createNotification());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
        }
        if (musicService != null) {
            musicService.pausePlayer();
            musicService.removeNotification();
        }
        instance = null; // Очистка статического экземпляра
    }

    public static MainActivity getInstance() {
        return instance;
    }
}