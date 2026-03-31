package com.yoben.tcp.rtspserver.media.source;

import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.media.MediaTrack;
import com.yoben.tcp.rtspserver.media.SyntheticAccessUnitSource;

/**
 * @Description 合成媒体源实现。
 * @Author Yoben
 * @Since 2026-03-25 16:50:00
 */
public class SyntheticMediaSource implements AccessUnitSource {

    private final SyntheticAccessUnitSource delegate = new SyntheticAccessUnitSource();

    @Override
    public byte[] nextAccessUnit(MediaSession session, MediaTrack track, long frameIndex) {
        return delegate.nextAccessUnit(track, frameIndex);
    }
}
