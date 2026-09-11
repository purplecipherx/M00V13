package com.m00v13.tv;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

/** Media3 renderer factory that makes the passthrough setting real. */
@UnstableApi
public final class M00v13RenderersFactory extends DefaultRenderersFactory {
    private final boolean passthrough;
    public M00v13RenderersFactory(Context context,boolean passthrough){super(context);this.passthrough=passthrough;}

    @Override @Nullable
    protected AudioSink buildAudioSink(Context context,boolean enableFloatOutput,boolean enableAudioOutputPlaybackParams){
        if(passthrough){
            DebugLog.append(context,"AUDIO","Media3 sink: automatic encoded passthrough enabled");
            return new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams).build();
        }
        DebugLog.append(context,"AUDIO","Media3 sink: passthrough disabled; PCM/default capabilities forced");
        return new DefaultAudioSink.Builder((Context)null).setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES).setEnableFloatOutput(enableFloatOutput).setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams).build();
    }
}
