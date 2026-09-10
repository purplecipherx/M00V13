package com.m00v13.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.Locale;

public final class PlayerActivity extends Activity {
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_MEDIA_ID = "media_id";
    public static final String EXTRA_FALLBACK_URIS = "fallback_uris";

    private ExoPlayer player;
    private PlayerView view;
    private ProfileStore profiles;
    private String mediaId;
    private ArrayList<String> fallbacks = new ArrayList<>();
    private int fallbackIndex = 0;
    private long lastPositionMs = 0L;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private boolean textTracksDisabledByUser = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profiles = new ProfileStore(this);
        mediaId = getIntent().getStringExtra(EXTRA_MEDIA_ID);
        String raw = getIntent().getStringExtra(EXTRA_URI);
        ArrayList<String> supplied = getIntent().getStringArrayListExtra(EXTRA_FALLBACK_URIS);
        if (supplied != null) fallbacks.addAll(supplied);
        if (raw == null || raw.trim().isEmpty()) { finish(); return; }

        view = new PlayerView(this);
        view.setUseController(true);
        view.setControllerAutoShow(true);
        view.setControllerShowTimeoutMs(5000);
        view.setResizeMode(resizeMode);
        view.setKeepScreenOn(true);
        setContentView(view);

        player = new ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(30_000L)
            .build();
        view.setPlayer(player);

        String preferred = profiles.preferredLanguage();
        TrackSelectionParameters initial = player.getTrackSelectionParameters().buildUpon()
            .setPreferredAudioLanguage(preferred)
            .setPreferredTextLanguage(preferred)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build();
        player.setTrackSelectionParameters(initial);

        player.addListener(new Player.Listener() {
            @Override public void onTracksChanged(Tracks tracks) { applyLanguagePolicy(tracks, preferred); }
            @Override public void onPlayerError(PlaybackException error) { tryFallback(); }
        });

        lastPositionMs = mediaId == null ? 0L : profiles.progressMs(mediaId);
        playUri(raw, lastPositionMs);
    }

    private void playUri(String raw, long positionMs) {
        player.setMediaItem(new MediaItem.Builder().setUri(Uri.parse(raw)).setMediaId(mediaId == null ? raw : mediaId).build());
        player.prepare();
        if (positionMs > 0) player.seekTo(positionMs);
        player.play();
    }

    private void tryFallback() {
        if (player == null || fallbackIndex >= fallbacks.size()) return;
        long resume = Math.max(lastPositionMs, player.getCurrentPosition());
        String next = fallbacks.get(fallbackIndex++);
        playUri(next, resume);
    }

    private void applyLanguagePolicy(Tracks tracks, String preferred) {
        boolean preferredAudioExists = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                String language = group.getTrackFormat(i).language;
                if (languageMatches(language, preferred)) { preferredAudioExists = true; break; }
            }
            if (preferredAudioExists) break;
        }

        boolean disableText = textTracksDisabledByUser || preferredAudioExists;
        TrackSelectionParameters updated = player.getTrackSelectionParameters().buildUpon()
            .setPreferredAudioLanguage(preferred)
            .setPreferredTextLanguage(preferred)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disableText)
            .build();
        if (!updated.equals(player.getTrackSelectionParameters())) player.setTrackSelectionParameters(updated);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (player == null || event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                player.seekBack(); view.showController(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                player.seekForward(); view.showController(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player.isPlaying()) player.pause(); else player.play();
                view.showController(); return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                player.seekBack(); view.showController(); return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                player.seekForward(); view.showController(); return true;
            case KeyEvent.KEYCODE_MENU:
                cycleResizeMode(); return true;
            case KeyEvent.KEYCODE_CAPTIONS:
                toggleSubtitles(); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                view.showController(); return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void cycleResizeMode() {
        if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
        else if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
        else resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
        view.setResizeMode(resizeMode);
        String label = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT ? "Fit" : resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM ? "Zoom" : "Fill";
        Toast.makeText(this, "Aspect: " + label, Toast.LENGTH_SHORT).show();
    }

    private void toggleSubtitles() {
        textTracksDisabledByUser = !player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        TrackSelectionParameters p = player.getTrackSelectionParameters().buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, textTracksDisabledByUser)
            .build();
        player.setTrackSelectionParameters(p);
        Toast.makeText(this, textTracksDisabledByUser ? "Subtitles off" : "Subtitles on", Toast.LENGTH_SHORT).show();
    }

    private boolean languageMatches(String candidate, String preferred) {
        if (candidate == null || preferred == null) return false;
        String a = candidate.toLowerCase(Locale.US);
        String b = preferred.toLowerCase(Locale.US);
        return a.equals(b) || a.startsWith(b + "-") || b.startsWith(a + "-");
    }

    private void saveProgress() {
        if (player != null && mediaId != null) {
            long duration = player.getDuration();
            if (duration > 0 && duration != C.TIME_UNSET) profiles.saveProgress(mediaId, player.getCurrentPosition(), duration);
        }
    }

    @Override protected void onPause() { saveProgress(); super.onPause(); }
    @Override protected void onStop() { saveProgress(); super.onStop(); }

    @Override protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
