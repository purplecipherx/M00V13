package com.m00v13.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.Locale;

public final class PlayerActivity extends Activity {
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_MEDIA_ID = "media_id";

    private ExoPlayer player;
    private ProfileStore profiles;
    private String mediaId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profiles = new ProfileStore(this);
        mediaId = getIntent().getStringExtra(EXTRA_MEDIA_ID);
        String raw = getIntent().getStringExtra(EXTRA_URI);
        if (raw == null || raw.isBlank()) { finish(); return; }

        PlayerView view = new PlayerView(this);
        view.setUseController(true);
        setContentView(view);

        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);

        String preferred = profiles.preferredLanguage();
        TrackSelectionParameters initial = player.getTrackSelectionParameters().buildUpon()
            .setPreferredAudioLanguage(preferred)
            .setPreferredTextLanguage(preferred)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build();
        player.setTrackSelectionParameters(initial);

        player.addListener(new Player.Listener() {
            @Override public void onTracksChanged(Tracks tracks) {
                applyLanguagePolicy(tracks, preferred);
            }
        });

        player.setMediaItem(new MediaItem.Builder().setUri(Uri.parse(raw)).setMediaId(mediaId == null ? raw : mediaId).build());
        player.prepare();
        player.play();
    }

    private void applyLanguagePolicy(Tracks tracks, String preferred) {
        boolean preferredAudioExists = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                String language = group.getTrackFormat(i).language;
                if (languageMatches(language, preferred)) {
                    preferredAudioExists = true;
                    break;
                }
            }
            if (preferredAudioExists) break;
        }

        TrackSelectionParameters updated = player.getTrackSelectionParameters().buildUpon()
            .setPreferredAudioLanguage(preferred)
            .setPreferredTextLanguage(preferred)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, preferredAudioExists)
            .build();
        if (!updated.equals(player.getTrackSelectionParameters())) {
            player.setTrackSelectionParameters(updated);
        }
    }

    private boolean languageMatches(String candidate, String preferred) {
        if (candidate == null || preferred == null) return false;
        String a = candidate.toLowerCase(Locale.US);
        String b = preferred.toLowerCase(Locale.US);
        return a.equals(b) || a.startsWith(b + "-") || b.startsWith(a + "-");
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null && mediaId != null) {
            profiles.saveProgress(mediaId, player.getCurrentPosition(), player.getDuration());
        }
    }

    @Override protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
