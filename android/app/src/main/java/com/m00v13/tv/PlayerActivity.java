package com.m00v13.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerActivity extends Activity {
    public static final String EXTRA_URI="uri",EXTRA_MEDIA_ID="media_id",EXTRA_FALLBACK_URIS="fallback_uris";
    private ExoPlayer player; private PlayerView view; private ProfileStore profiles; private String mediaId,preferred;
    private final ArrayList<String> fallbacks=new ArrayList<>(); private int fallbackIndex=0; private long lastPositionMs=0L;
    private int resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT; private boolean textTracksDisabledByUser=false,dialogOpen=false;
    private long lastSeekAt=0L; private int seekBurst=0;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);TvUi.disableWindowAnimations(this);getWindow().getDecorView().setBackgroundColor(Color.BLACK);profiles=new ProfileStore(this);preferred=profiles.preferredLanguage();mediaId=getIntent().getStringExtra(EXTRA_MEDIA_ID);String raw=getIntent().getStringExtra(EXTRA_URI);ArrayList<String> supplied=getIntent().getStringArrayListExtra(EXTRA_FALLBACK_URIS);if(supplied!=null)fallbacks.addAll(supplied);if(raw==null||raw.trim().isEmpty()){finish();return;}
        view=new PlayerView(this);view.setBackgroundColor(Color.BLACK);view.setShutterBackgroundColor(Color.BLACK);view.setUseController(true);view.setControllerAutoShow(true);view.setControllerShowTimeoutMs(5000);view.setResizeMode(resizeMode);view.setKeepScreenOn(true);setContentView(view);
        player=new ExoPlayer.Builder(this).setSeekBackIncrementMs(10000L).setSeekForwardIncrementMs(30000L).build();view.setPlayer(player);
        TrackSelectionParameters initial=player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage(preferred).setPreferredTextLanguage(preferred).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build();player.setTrackSelectionParameters(initial);
        player.addListener(new Player.Listener(){@Override public void onTracksChanged(Tracks tracks){applyLanguagePolicy(tracks,preferred);logTracks(tracks);}@Override public void onPlayerError(PlaybackException error){DebugLog.append(PlayerActivity.this,"PLAYER","Error code="+error.errorCode+": "+error.getMessage());tryFallback();}@Override public void onPlaybackStateChanged(int state){DebugLog.append(PlayerActivity.this,"PLAYER","state="+state+" pos="+player.getCurrentPosition());}});
        AudioCapabilities.log(this);lastPositionMs=mediaId==null?0L:profiles.progressMs(mediaId);playUri(raw,lastPositionMs);
    }

    private void playUri(String raw,long positionMs){DebugLog.append(this,"PLAYER","Play "+safeUri(raw)+" resume="+positionMs);player.setMediaItem(new MediaItem.Builder().setUri(Uri.parse(raw)).setMediaId(mediaId==null?raw:mediaId).build());player.prepare();if(positionMs>0)player.seekTo(positionMs);player.play();}
    private void tryFallback(){if(player==null||fallbackIndex>=fallbacks.size())return;long resume=Math.max(lastPositionMs,player.getCurrentPosition());String next=fallbacks.get(fallbackIndex++);DebugLog.append(this,"PLAYER","Trying fallback "+fallbackIndex+"/"+fallbacks.size());playUri(next,resume);}

    private void applyLanguagePolicy(Tracks tracks,String pref){boolean preferredAudioExists=false;for(Tracks.Group group:tracks.getGroups()){if(group.getType()!=C.TRACK_TYPE_AUDIO)continue;for(int i=0;i<group.length;i++){if(languageMatches(group.getTrackFormat(i).language,pref)){preferredAudioExists=true;break;}}if(preferredAudioExists)break;}boolean disableText=textTracksDisabledByUser||preferredAudioExists;TrackSelectionParameters updated=player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage(pref).setPreferredTextLanguage(pref).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,disableText).build();if(!updated.equals(player.getTrackSelectionParameters()))player.setTrackSelectionParameters(updated);}

    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(dialogOpen)return super.dispatchKeyEvent(event);
        if(player==null||event.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(event);
        switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_DPAD_LEFT: acceleratedSeek(false);return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: acceleratedSeek(true);return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND: acceleratedSeek(false);return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: acceleratedSeek(true);return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:if(player.isPlaying())player.pause();else player.play();view.showController();return true;
            case KeyEvent.KEYCODE_MENU:case KeyEvent.KEYCODE_SETTINGS:showPlayerOptions();return true;
            case KeyEvent.KEYCODE_CAPTIONS:toggleSubtitles();return true;
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:view.showController();return super.dispatchKeyEvent(event);
            default:return super.dispatchKeyEvent(event);
        }
    }

    private void acceleratedSeek(boolean forward){long now=SystemClock.elapsedRealtime();if(now-lastSeekAt>1200)seekBurst=0;else seekBurst++;lastSeekAt=now;long step=seekBurst>=6?120000L:seekBurst>=4?60000L:seekBurst>=2?30000L:10000L;long duration=player.getDuration();long target=player.getCurrentPosition()+(forward?step:-step);target=Math.max(0,target);if(duration>0&&duration!=C.TIME_UNSET)target=Math.min(duration,target);player.seekTo(target);view.showController();if(seekBurst==0||seekBurst==2||seekBurst==4||seekBurst==6)Toast.makeText(this,(forward?"+":"-")+(step/1000)+"s scrub",Toast.LENGTH_SHORT).show();}

    private AlertDialog showListDialog(String title,String[] items,android.content.DialogInterface.OnClickListener click){dialogOpen=true;AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setItems(items,click).create();d.setOnDismissListener(x->dialogOpen=false);d.show();return d;}
    private void showPlayerOptions(){String[] options={"Aspect ratio","Playback speed","Audio track","Subtitles","Audio passthrough","Playback diagnostics"};showListDialog("Playback options",options,(d,w)->{switch(w){case 0:showAspectOptions();break;case 1:showSpeedOptions();break;case 2:showTrackOptions(C.TRACK_TYPE_AUDIO);break;case 3:showSubtitleOptions();break;case 4:showPassthroughInfo();break;case 5:showPlaybackDiagnostics();break;}});}
    private void showAspectOptions(){String[] labels={"Fit — preserve source aspect ratio","Zoom — crop edges","Fill — stretch to screen"};showListDialog("Aspect ratio",labels,(d,w)->{resizeMode=w==0?AspectRatioFrameLayout.RESIZE_MODE_FIT:w==1?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FILL;view.setResizeMode(resizeMode);DebugLog.append(this,"PLAYER","Aspect="+labels[w]);});}
    private void showSpeedOptions(){final float[] speeds={0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f};String[] labels=new String[speeds.length];for(int i=0;i<speeds.length;i++)labels[i]=speeds[i]+"×";showListDialog("Playback speed",labels,(d,w)->{player.setPlaybackSpeed(speeds[w]);DebugLog.append(this,"PLAYER","Speed="+speeds[w]);});}
    private void showTrackOptions(int type){List<TrackChoice> choices=trackChoices(type);if(choices.isEmpty()){Toast.makeText(this,"No selectable tracks",Toast.LENGTH_SHORT).show();return;}String[] labels=new String[choices.size()];for(int i=0;i<choices.size();i++)labels[i]=choices.get(i).label;showListDialog(type==C.TRACK_TYPE_AUDIO?"Audio track":"Subtitle track",labels,(d,w)->selectTrack(type,choices.get(w)));}
    private void showSubtitleOptions(){List<TrackChoice> choices=trackChoices(C.TRACK_TYPE_TEXT);String[] labels=new String[choices.size()+1];labels[0]="Off";for(int i=0;i<choices.size();i++)labels[i+1]=choices.get(i).label;showListDialog("Subtitles",labels,(d,w)->{if(w==0){textTracksDisabledByUser=true;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}else{textTracksDisabledByUser=false;selectTrack(C.TRACK_TYPE_TEXT,choices.get(w-1));}});}
    private void selectTrack(int type,TrackChoice choice){TrackSelectionOverride override=new TrackSelectionOverride(choice.group.getMediaTrackGroup(),choice.index);TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).addOverride(override).setTrackTypeDisabled(type,false);player.setTrackSelectionParameters(b.build());DebugLog.append(this,"PLAYER",(type==C.TRACK_TYPE_AUDIO?"Audio":"Subtitle")+" track="+choice.label);}
    private List<TrackChoice> trackChoices(int type){ArrayList<TrackChoice> out=new ArrayList<>();for(Tracks.Group group:player.getCurrentTracks().getGroups()){if(group.getType()!=type)continue;for(int i=0;i<group.length;i++){if(!group.isTrackSupported(i))continue;Format f=group.getTrackFormat(i);out.add(new TrackChoice(group,i,formatLabel(f,type,group.isTrackSelected(i))));}}return out;}
    private String formatLabel(Format f,int type,boolean selected){String lang=f.language==null||f.language.isEmpty()?"Unknown":f.language.toUpperCase(Locale.US);StringBuilder s=new StringBuilder(selected?"✓ ":"").append(lang);if(f.label!=null&&!f.label.isEmpty())s.append(" • ").append(f.label);if(type==C.TRACK_TYPE_AUDIO){if(f.channelCount>0)s.append(" • ").append(f.channelCount).append("ch");if(f.sampleRate>0)s.append(" • ").append(f.sampleRate/1000f).append("kHz");}if(f.sampleMimeType!=null)s.append(" • ").append(f.sampleMimeType.replace("audio/","").replace("text/",""));return s.toString();}
    private void showPassthroughInfo(){String summary=AudioCapabilities.log(this);boolean enabled=new AppSettingsStore(this).automaticPassthrough();dialogOpen=true;AlertDialog d=new AlertDialog.Builder(this).setTitle("Audio passthrough").setMessage((enabled?"Automatic passthrough enabled.\n\n":"Automatic passthrough disabled.\n\n")+summary).setPositiveButton("OK",null).create();d.setOnDismissListener(x->dialogOpen=false);d.show();}
    private void showPlaybackDiagnostics(){Format v=player.getVideoFormat(),a=player.getAudioFormat();String video=v==null?"Video: unknown":"Video: "+v.width+"×"+v.height+" • "+v.frameRate+" fps • "+nullSafe(v.sampleMimeType);String audio=a==null?"Audio: unknown":"Audio: "+nullSafe(a.sampleMimeType)+" • "+a.channelCount+"ch • "+nullSafe(a.language);String message=video+"\n"+audio+"\nPosition: "+(player.getCurrentPosition()/1000)+"s / "+(player.getDuration()/1000)+"s\nSpeed: "+player.getPlaybackParameters().speed+"×\n"+AudioCapabilities.log(this);DebugLog.append(this,"PLAYER",message.replace('\n',' '));dialogOpen=true;AlertDialog d=new AlertDialog.Builder(this).setTitle("Playback diagnostics").setMessage(message).setPositiveButton("OK",null).create();d.setOnDismissListener(x->dialogOpen=false);d.show();}
    private void toggleSubtitles(){boolean currentlyDisabled=player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);textTracksDisabledByUser=!currentlyDisabled;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,textTracksDisabledByUser).build());Toast.makeText(this,textTracksDisabledByUser?"Subtitles off":"Subtitles on",Toast.LENGTH_SHORT).show();}
    private void logTracks(Tracks tracks){int audio=0,text=0,video=0;for(Tracks.Group g:tracks.getGroups()){if(g.getType()==C.TRACK_TYPE_AUDIO)audio+=g.length;else if(g.getType()==C.TRACK_TYPE_TEXT)text+=g.length;else if(g.getType()==C.TRACK_TYPE_VIDEO)video+=g.length;}DebugLog.append(this,"PLAYER","Tracks video="+video+" audio="+audio+" subtitles="+text);}
    private boolean languageMatches(String candidate,String pref){if(candidate==null||pref==null)return false;String a=candidate.toLowerCase(Locale.US),b=pref.toLowerCase(Locale.US);return a.equals(b)||a.startsWith(b+"-")||b.startsWith(a+"-");}
    private String safeUri(String raw){try{Uri u=Uri.parse(raw);return u.getScheme()+"://"+(u.getHost()==null?"":u.getHost())+"/…";}catch(Exception e){return"<uri>";}}
    private static String nullSafe(String s){return s==null||s.isEmpty()?"unknown":s;}
    private void saveProgress(){if(player!=null&&mediaId!=null){long duration=player.getDuration();if(duration>0&&duration!=C.TIME_UNSET)profiles.saveProgress(mediaId,player.getCurrentPosition(),duration);}}
    @Override protected void onPause(){saveProgress();super.onPause();}@Override protected void onStop(){saveProgress();super.onStop();}@Override protected void onDestroy(){if(player!=null){player.release();player=null;}super.onDestroy();}
    private static final class TrackChoice{final Tracks.Group group;final int index;final String label;TrackChoice(Tracks.Group g,int i,String l){group=g;index=i;label=l;}}
}
