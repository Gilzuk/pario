package com.example.pairo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;

/**
 * Music and sound effects. The theme loops through MediaPlayer; short effects
 * play through SoundPool. All audio is original and rendered by tools/render.py
 * from tools/song.json (the web version synthesises the same notes live).
 */
final class Sound {
    enum Effect { MATCH, MISS, TICK, GO, FREEZE, WIN, TIMEUP, CLICK, STAR }

    private static final int[] RAW = {
            R.raw.sfx_match, R.raw.sfx_miss, R.raw.sfx_tick, R.raw.sfx_go,
            R.raw.sfx_freeze, R.raw.sfx_win, R.raw.sfx_timeup, R.raw.sfx_click, R.raw.sfx_star
    };
    private static final float MUSIC_VOLUME = 0.7f, HURRY_SPEED = 1.2f;
    private static final String MUTED_KEY = "muted";

    private final Context context;
    private final SharedPreferences prefs;
    private final SoundPool pool;
    private final int[] ids = new int[RAW.length];
    private final boolean[] loaded = new boolean[RAW.length];
    private MediaPlayer music;
    private boolean muted, resumed;
    private float speed = 1f;

    Sound(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        muted = prefs.getBoolean(MUTED_KEY, false);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            for (int i = 0; i < ids.length; i++) if (ids[i] == sampleId && status == 0) loaded[i] = true;
        });
        for (int i = 0; i < RAW.length; i++) ids[i] = pool.load(this.context, RAW[i], 1);
    }

    boolean isMuted() { return muted; }

    void setMuted(boolean m) {
        muted = m;
        prefs.edit().putBoolean(MUTED_KEY, m).apply();
        updateMusic();
    }

    void play(Effect e) {
        if (muted) return;
        int i = e.ordinal();
        if (loaded[i]) pool.play(ids[i], 1f, 1f, 1, 0, 1f);
    }

    /** Speeds the music up for the last seconds of a round. */
    void setHurry(boolean hurry) {
        float target = hurry ? HURRY_SPEED : 1f;
        if (target == speed || music == null || Build.VERSION.SDK_INT < 23) return;
        try {
            // Only while playing: on a paused player this call would start playback.
            if (!music.isPlaying()) return;
            music.setPlaybackParams(music.getPlaybackParams().setSpeed(target));
            speed = target;
        } catch (RuntimeException ignored) {
            // Some devices reject speed changes; the game works without it.
        }
    }

    void resume() {
        resumed = true;
        updateMusic();
    }

    void pause() {
        resumed = false;
        updateMusic();
    }

    void release() {
        pool.release();
        if (music != null) {
            music.release();
            music = null;
        }
    }

    private void updateMusic() {
        try {
            if (resumed && !muted) {
                ensureMusic();
                if (music != null && !music.isPlaying()) music.start();
            } else if (music != null && music.isPlaying()) {
                music.pause();
            }
        } catch (RuntimeException e) {
            // A player in an error state: drop it and rebuild next time.
            if (music != null) music.release();
            music = null;
        }
    }

    private void ensureMusic() {
        if (music != null) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            AssetFileDescriptor fd = context.getResources().openRawResourceFd(R.raw.music);
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            mp.setLooping(true);
            mp.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
            mp.prepare();
            music = mp;
            speed = 1f;
        } catch (Exception e) {
            music = null;
        }
    }
}
