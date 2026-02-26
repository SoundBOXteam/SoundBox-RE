package com.example.localmusicplayer;

public class Song {
    private String title;
    private String artist;
    private String album;
    private String duration;
    private String data;
    private String albumArt;
    private long id;
    private int durationInMs;

    public Song(String title, String artist, String album, String duration, String data, String albumArt, long id) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.data = data;
        this.albumArt = albumArt;
        this.id = id;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getAlbumArt() { return albumArt; }
    public void setAlbumArt(String albumArt) { this.albumArt = albumArt; }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getDurationInMs() { return durationInMs; }
    public void setDurationInMs(int durationInMs) { this.durationInMs = durationInMs; }
}