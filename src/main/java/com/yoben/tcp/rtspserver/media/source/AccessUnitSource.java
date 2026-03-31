package com.yoben.tcp.rtspserver.media.source;

import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.media.MediaTrack;
import java.util.List;

public interface AccessUnitSource {

    byte[] nextAccessUnit(MediaSession session, MediaTrack track, long frameIndex);

    default List<byte[]> bootstrapAccessUnits(MediaSession session, MediaTrack track) {
        return List.of();
    }

    default String describeFmtp(MediaSession session, MediaTrack track, String currentFmtp) {
        return currentFmtp;
    }

    default long frameIntervalMillis(MediaSession session, MediaTrack track, long frameIndex) {
        return Math.max(1, 1000L / Math.max(1, track.frameRate()));
    }

    default long frameIntervalNanos(MediaSession session, MediaTrack track, long frameIndex) {
        return Math.max(1L, frameIntervalMillis(session, track, frameIndex) * 1_000_000L);
    }

    default long timestampIncrement(MediaSession session, MediaTrack track, long frameIndex) {
        return Math.max(1, track.clockRate() / Math.max(1, track.frameRate()));
    }

    default long frameTimestamp(MediaSession session, MediaTrack track, long frameIndex, long timestampBase) {
        return timestampBase + frameIndex * Math.max(1L, timestampIncrement(session, track, frameIndex));
    }

    default long playbackStartFrameIndex(MediaSession session, MediaTrack track) {
        return 0L;
    }
}