package com.example.localmusicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    private final IBinder musicBind = new MusicBinder();
    private MediaPlayer mediaPlayer;
    private ArrayList<Song> songs;
    private int songPosition = 0;
    private boolean shuffle = false;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "MusicPlayerChannel";

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        initMediaPlayer();
        createNotificationChannel();
    }

    private void initMediaPlayer() {
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaPlayer.stop();
        mediaPlayer.release();
        return false;
    }

    public void setList(ArrayList<Song> theSongs) {
        songs = theSongs;
    }

    public void setSong(int songIndex) {
        songPosition = songIndex;
    }

    public void playSong() {
        if (mediaPlayer != null && songs != null && songs.size() > 0) {
            // ИСПРАВЛЕНО: Проверяем, что MediaPlayer в допустимом состоянии перед вызовом isPlaying()
            try {
                // ИСПРАВЛЕНО: Правильная логика остановки текущего трека перед воспроизведением нового
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();

                Song playSong = songs.get(songPosition);
                try {
                    mediaPlayer.setDataSource(playSong.getData());
                    mediaPlayer.prepareAsync();
                    mediaPlayer.setOnPreparedListener(mp -> {
                        mp.start();
                        // ИСПРАВЛЕНО: Обязательно вызываем startForeground при начале воспроизведения
                        Notification notification = createNotification();
                        if (notification != null) {
                            startForeground(NOTIFICATION_ID, notification);
                        }
                    });
                } catch (IOException e) {
                    Log.e("MUSIC_SERVICE", "Error playing song", e);
                }
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "MediaPlayer in illegal state", e);
                // Пересоздаем MediaPlayer, если он в недопустимом состоянии
                mediaPlayer.release();
                mediaPlayer = new MediaPlayer();
                initMediaPlayer();
                // Повторяем попытку
                try {
                    Song playSong = songs.get(songPosition);
                    mediaPlayer.setDataSource(playSong.getData());
                    mediaPlayer.prepareAsync();
                    mediaPlayer.setOnPreparedListener(mp -> {
                        mp.start();
                        Notification notification = createNotification();
                        if (notification != null) {
                            startForeground(NOTIFICATION_ID, notification);
                        }
                    });
                } catch (Exception ex) {
                    Log.e("MUSIC_SERVICE", "Error after recreating MediaPlayer", ex);
                }
            }
        }
    }

    public int getPosn() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "Error getting position", e);
                return 0;
            }
        }
        return 0;
    }

    public int getDur() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "Error getting duration", e);
                return 0;
            }
        }
        return 0;
    }

    public boolean isPlaying() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "Error checking if playing", e);
                return false;
            }
        }
        return false;
    }

    public void pausePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "Error pausing player", e);
            }
        }
        // ИСПРАВЛЕНО: При паузе НЕ вызываем stopForeground, а обновляем уведомление
        updateNotification(createNotification());
    }

    public void seek(int posn) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(posn);
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "Error seeking", e);
            }
        }
    }

    public void go() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "Error starting player", e);
            }
        }
        updateNotification(createNotification());
    }

    public void playNext() {
        if (songs != null && songs.size() > 0) {
            songPosition++;
            if (songPosition >= songs.size()) {
                songPosition = 0;
            }
            playSong(); // playSong вызовет startForeground
        }
    }

    public void playPrev() {
        if (songs != null && songs.size() > 0) {
            songPosition--;
            if (songPosition < 0) {
                songPosition = songs.size() - 1;
            }
            playSong(); // playSong вызовет startForeground
        }
    }

    public void setShuffle() {
        shuffle = !shuffle;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public int getCurrentSongPosition() {
        return songPosition;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp != null) {
            try {
                if (mp.getCurrentPosition() == mp.getDuration()) {
                    playNext();
                }
            } catch (IllegalStateException e) {
                Log.e("MUSIC_SERVICE", "Error on completion", e);
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e("MUSIC_SERVICE", "Media player error: " + what + ", " + extra);
        return false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player Channel",
                    NotificationManager.IMPORTANCE_LOW // ИСПРАВЛЕНО: Уровень важности для уведомления в трее
            );
            channel.setDescription("Channel for music player notifications");
            // ИСПРАВЛЕНО: Устанавливаем, что уведомления могут быть показаны в трее
            channel.setShowBadge(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    public Notification createNotification() {
        if (songs != null && songPosition >= 0 && songPosition < songs.size()) {
            Song currentSong = songs.get(songPosition);

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Bitmap albumArt = null;
            try {
                retriever.setDataSource(currentSong.getData());
                byte[] art = retriever.getEmbeddedPicture();
                if (art != null) {
                    albumArt = BitmapFactory.decodeByteArray(art, 0, art.length);
                }
            } catch (Exception e) {
                Log.e("MUSIC_SERVICE", "Error retrieving album art", e);
                // e.printStackTrace(); // Не вызываем printStackTrace в production
            } finally {
                // ИСПРАВЛЕНО: Правильное использование finally блока для освобождения ресурсов
                try {
                    retriever.release();
                } catch (IOException e) {
                    // Игнорируем ошибки при освобождении, если они возникнут
                    Log.w("MUSIC_SERVICE", "Error releasing MediaMetadataRetriever", e);
                }
            }

            if (albumArt == null) {
                albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.ic_music_note);
            }

            // ИСПРАВЛЕНО: Создание PendingIntent для кнопок управления
            Intent prevIntent = new Intent(this, NotificationReceiver.class);
            prevIntent.setAction("PREVIOUS");
            PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, 0,
                    prevIntent, PendingIntent.FLAG_IMMUTABLE);

            Intent playPauseIntent = new Intent(this, NotificationReceiver.class);
            playPauseIntent.setAction("PLAYPAUSE");
            PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(this, 0,
                    playPauseIntent, PendingIntent.FLAG_IMMUTABLE);

            Intent nextIntent = new Intent(this, NotificationReceiver.class);
            nextIntent.setAction("NEXT");
            PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 0,
                    nextIntent, PendingIntent.FLAG_IMMUTABLE);

            // ИСПРАВЛЕНО: Убираем MediaStyle, так как она может конфликтовать с startForeground
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(currentSong.getTitle())
                    .setContentText(currentSong.getArtist())
                    .setSmallIcon(R.drawable.ic_music_note)
                    .setLargeIcon(albumArt)
                    .setContentIntent(contentIntent)
                    .addAction(R.drawable.ic_skip_previous, "Previous", prevPendingIntent)
                    .addAction(isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                            isPlaying() ? "Pause" : "Play", playPausePendingIntent)
                    .addAction(R.drawable.ic_skip_next, "Next", nextPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW) // ИСПРАВЛЕНО: Приоритет уведомления
                    .build();
        }
        return null;
    }

    // ИСПРАВЛЕНО: Метод updateNotification теперь использует notify, а не startForeground
    public void updateNotification(Notification notification) {
        if (notification != null) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    public void removeNotification() {
        stopForeground(true);
    }
}