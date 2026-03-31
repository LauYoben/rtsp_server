package com.yoben.tcp.rtspserver.media;

import java.nio.file.Path;
import java.util.List;

/**
 * @Description 媒体会话元数据记录类型。
 * @Author Yoben
 * @Since 2026-03-18 15:15:00
 */
public record MediaSession(
        String path,
        String sessionName,
        List<MediaTrack> tracks,
        Path sourceFile,
        boolean synthetic) {
}
